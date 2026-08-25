package gregtech.api.capability.impl.boiler;

import gregtech.api.GTValues;
import gregtech.api.capability.GregtechDataCodes;
import gregtech.api.capability.IMultipleTankHandler;
import gregtech.api.capability.impl.CommonFluidFilters;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.unification.material.Materials;
import gregtech.api.util.GTUtility;
import gregtech.common.ConfigHolder;
import gregtech.common.metatileentities.multi.MetaTileEntityLargeBoiler;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;

/**
 * Orchestrates a {@link BoilerThermalModel} against a {@link MetaTileEntityLargeBoiler}: finds and consumes fuel,
 * feeds it into the model, resolves each tick's water/steam I/O against the multiblock's tanks, and applies
 * explosions.
 * <p>
 * This class intentionally has no relation to {@code AbstractRecipeLogic} / {@code BoilerRecipeLogic} (which is now
 * {@code @Deprecated}). It is the standalone replacement; the old implementation is kept only until this one has
 * been playtested, at which point it will be removed entirely.
 * <p>
 * Only {@link BoilerThermalModel} is covered by unit tests, since this class's behavior is inherently tied to
 * Minecraft's item/fluid/recipe systems; this class itself is validated by playtesting.
 * <p>
 * Instances are server-authoritative: {@link #update()} is a no-op on the client side, and the handful of fields
 * that affect client-visible state ({@link #isWorkingEnabled()}, {@link #getLastTickSteam()} used by
 * {@link #isActive()} for rendering) are synced explicitly via {@link #writeInitialSyncData(PacketBuffer)} and
 * {@link #receiveCustomData(int, PacketBuffer)}.
 */
public final class BoilerLogic {

    /** EU of fuel heat per tick of vanilla furnace burn time. */
    private static final int EU_PER_SOLID_BURNTIME = 20;
    /** How many ticks of fuel the buffer should try to stay stocked with. */
    private static final int BUFFER_TICKS = 80;

    private final MetaTileEntityLargeBoiler boiler;
    private final BoilerThermalModel model;

    private boolean workingEnabled = true;
    private int lastTickSteam;

    public BoilerLogic(@NotNull MetaTileEntityLargeBoiler boiler, @NotNull BoilerThermalModel model) {
        this.boiler = boiler;
        this.model = model;
    }

    public void update() {
        if (boiler.getWorld().isRemote) return;

        if (!workingEnabled || !canOperate()) {
            setLastTickSteam(0);
            return;
        }

        model.setThrottle(boiler.getThrottle() / 100.0);

        if (model.needsFuel(BUFFER_TICKS)) {
            tryFindFuel();
        }

        int availableWater = drainBoilerWater(Integer.MAX_VALUE, false);
        BoilerTickResult result = model.tick(availableWater, getBurnEfficiencyFromMaintenance());

        if (result.waterConsumed() > 0) {
            drainBoilerWater(result.waterConsumed(), true);
        }
        if (result.steamGenerated() > 0) {
            boiler.getExportFluids().fill(Materials.Steam.getFluid(result.steamGenerated()), true);
        }
        setLastTickSteam(result.steamGenerated());

        if (result.exploded()) {
            boiler.explodeMultiblock(result.explosionPower());
        }
    }

    private boolean canOperate() {
        if (ConfigHolder.machines.enableMaintenance && boiler.hasMaintenanceMechanics() &&
                boiler.getNumMaintenanceProblems() > 5) {
            return false;
        }
        return true;
    }

    private double getBurnEfficiencyFromMaintenance() {
        if (ConfigHolder.machines.enableMaintenance) {
            return Math.max(0, 1 - 0.1 * boiler.getNumMaintenanceProblems());
        }
        return 1.0;
    }

    /**
     * Looks for one unit of fuel (liquid or solid) in the boiler's inputs, consumes it, and feeds its energy value
     * into the thermal model. Consumes at most one fuel source per call; {@link #update()} relies on being called
     * every tick to keep the buffer stocked.
     */
    private void tryFindFuel() {
        IMultipleTankHandler importFluids = boiler.getImportFluids();
        for (IFluidTank fluidTank : importFluids.getFluidTanks()) {
            FluidStack fuelStack = fluidTank.drain(Integer.MAX_VALUE, false);
            if (fuelStack == null || CommonFluidFilters.BOILER_FLUID.test(fuelStack)) continue;

            if (tryBurnFluidFuel(fluidTank, fuelStack, RecipeMaps.COMBUSTION_GENERATOR_FUELS)) return;
            if (tryBurnFluidFuel(fluidTank, fuelStack, RecipeMaps.SEMI_FLUID_GENERATOR_FUELS)) return;
        }

        IItemHandlerModifiable importItems = boiler.getImportItems();
        for (int i = 0; i < importItems.getSlots(); i++) {
            ItemStack stack = importItems.getStackInSlot(i);
            if (stack.isEmpty() || FluidUtil.getFluidHandler(stack) != null) continue;

            int burnTime = TileEntityFurnace.getItemBurnTime(stack);
            if (burnTime > 0) {
                importItems.extractItem(i, 1, false);
                model.addFuelEU((long) burnTime * EU_PER_SOLID_BURNTIME);
                return;
            }
        }
    }

    private boolean tryBurnFluidFuel(@NotNull IFluidTank fluidTank, @NotNull FluidStack fuelStack,
                                     @NotNull gregtech.api.recipes.RecipeMap<?> fuelMap) {
        Recipe recipe = fuelMap.findRecipe(GTValues.V[GTValues.MAX], Collections.emptyList(),
                Collections.singletonList(fuelStack));
        if (recipe == null) return false;
        int required = recipe.getFluidInputs().get(0).getAmount();
        if (fuelStack.amount < required) return false;

        fluidTank.drain(required, true);
        model.addFuelEU(Math.abs(recipe.getEUt()) * (long) recipe.getDuration());
        return true;
    }

    /**
     * Drains (or simulates draining) boiler-acceptable water from the import tanks, trying plain water, distilled
     * water, and any config-registered boiler fluids in that order.
     *
     * @param amount  the maximum amount (mB) to drain.
     * @param doDrain whether to actually remove the fluid, or just report how much is available.
     * @return the amount (mB) drained or available.
     */
    private int drainBoilerWater(int amount, boolean doDrain) {
        if (amount <= 0) return 0;
        IMultipleTankHandler tanks = boiler.getImportFluids();
        int drained = 0;

        FluidStack drainedWater = tanks.drain(Materials.Water.getFluid(amount), doDrain);
        if (drainedWater != null) drained += drainedWater.amount;

        if (drained < amount) {
            drainedWater = tanks.drain(Materials.DistilledWater.getFluid(amount - drained), doDrain);
            if (drainedWater != null) drained += drainedWater.amount;
        }

        if (drained < amount) {
            for (String fluidName : ConfigHolder.machines.boilerFluids) {
                Fluid fluid = FluidRegistry.getFluid(fluidName);
                if (fluid == null) continue;
                drainedWater = tanks.drain(new FluidStack(fluid, amount - drained), doDrain);
                if (drainedWater != null) drained += drainedWater.amount;
                if (drained >= amount) break;
            }
        }

        return drained;
    }

    public boolean isActive() {
        return workingEnabled && lastTickSteam > 0;
    }

    public boolean isWorkingEnabled() {
        return workingEnabled;
    }

    public void setWorkingEnabled(boolean workingEnabled) {
        if (workingEnabled != this.workingEnabled && !boiler.getWorld().isRemote) {
            boiler.writeCustomData(GregtechDataCodes.WORKING_ENABLED, buf -> buf.writeBoolean(workingEnabled));
        }
        this.workingEnabled = workingEnabled;
    }

    public int getLastTickSteam() {
        return lastTickSteam;
    }

    private void setLastTickSteam(int lastTickSteam) {
        if (lastTickSteam != this.lastTickSteam && !boiler.getWorld().isRemote) {
            boiler.writeCustomData(GregtechDataCodes.BOILER_LAST_TICK_STEAM, buf -> buf.writeVarInt(lastTickSteam));
        }
        this.lastTickSteam = lastTickSteam;
    }

    /**
     * Writes the subset of state needed for client-side rendering ({@link #isActive()} via
     * {@link #isWorkingEnabled()} and {@link #getLastTickSteam()}) when a client first loads this multiblock.
     */
    public void writeInitialSyncData(@NotNull PacketBuffer buf) {
        buf.writeBoolean(workingEnabled);
        buf.writeVarInt(lastTickSteam);
    }

    /**
     * @see #writeInitialSyncData(PacketBuffer)
     */
    public void receiveInitialSyncData(@NotNull PacketBuffer buf) {
        this.workingEnabled = buf.readBoolean();
        this.lastTickSteam = buf.readVarInt();
    }

    /**
     * Handles incremental updates sent via {@link #setWorkingEnabled(boolean)} / {@link #setLastTickSteam(int)}.
     * Callers should forward {@code discriminator}/{@code buf} from the owning MTE's own
     * {@code receiveCustomData}.
     */
    public void receiveCustomData(int discriminator, @NotNull PacketBuffer buf) {
        if (discriminator == GregtechDataCodes.WORKING_ENABLED) {
            this.workingEnabled = buf.readBoolean();
        } else if (discriminator == GregtechDataCodes.BOILER_LAST_TICK_STEAM) {
            this.lastTickSteam = buf.readVarInt();
        }
    }

    /**
     * @return the chassis heat as a percentage (0-100) of the currently throttled target, for UI display.
     */
    public int getHeatScaled() {
        return model.getHeatPercentage();
    }

    public void invalidate() {
        setLastTickSteam(0);
    }

    @NotNull
    public NBTTagCompound serializeNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setLong("ChassisHeat", model.getChassisHeat());
        tag.setLong("RemainingBurnEU", model.getRemainingBurnEU());
        tag.setBoolean("Dry", model.isDry());
        tag.setBoolean("WorkingEnabled", workingEnabled);
        return tag;
    }

    public void deserializeNBT(@Nullable NBTTagCompound tag) {
        if (tag == null) return;
        model.loadState(tag.getLong("ChassisHeat"), tag.getLong("RemainingBurnEU"), tag.getBoolean("Dry"));
        this.workingEnabled = !tag.hasKey("WorkingEnabled") || tag.getBoolean("WorkingEnabled");
    }
}
