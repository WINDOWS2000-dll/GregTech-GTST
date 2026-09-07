package gregtech.common.metatileentities.multi.electric;

import gregtech.api.block.IHeatingCoilBlockStats;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.metatileentity.multiblock.IMultiblockPart;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.ui.MultiblockUIBuilder;
import gregtech.api.pattern.BlockPattern;
import gregtech.api.pattern.FactoryBlockPattern;
import gregtech.api.pattern.PatternMatchContext;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.logic.statemachine.RecipeLogicConfig;
import gregtech.api.util.GTUtility;
import gregtech.api.util.KeyUtil;
import gregtech.client.renderer.ICubeRenderer;
import gregtech.client.renderer.texture.Textures;
import gregtech.common.blocks.BlockMachineCasing.MachineCasingType;
import gregtech.common.blocks.MetaBlocks;
import gregtech.core.sound.GTSoundEvents;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.SoundEvent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.cleanroommc.modularui.api.drawable.IKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MetaTileEntityPyrolyseOven extends RecipeWorkableMultiblockController {

    private int coilTier = -1;

    public MetaTileEntityPyrolyseOven(ResourceLocation metaTileEntityId) {
        super(metaTileEntityId, RecipeMaps.PYROLYSE_RECIPES);
    }

    /**
     * Replaces legacy's {@code PyrolyseOvenWorkableHandler}
     * ({@code modifyOverclockPost}, an inner-class-spam case this design avoids) with
     * {@link RecipeLogicConfig#overclock}'s
     * {@code durationDiscount} seam (applied post-overclock, exactly where legacy's hook ran), reading the same
     * verbatim formula: 75% speed on cupronickel ({@code coilTier == 0}), each coil tier above kanthal
     * ({@code coilTier >= 1}) 50% faster than the last.
     */
    @Override
    protected @NotNull RecipeLogicConfig createConfig() {
        RecipeLogicConfig config = super.createConfig();
        config.overclock.durationDiscount = () -> coilTier < 0 ? 1.0 :
                coilTier == 0 ? 4.0 / 3 : 2.0 / (coilTier + 1);
        return config;
    }

    @Override
    public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
        return new MetaTileEntityPyrolyseOven(metaTileEntityId);
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
                .aisle("XXX", "XXX", "XXX")
                .aisle("CCC", "C#C", "CCC")
                .aisle("CCC", "C#C", "CCC")
                .aisle("XXX", "XSX", "XXX")
                .where('S', selfPredicate())
                .where('X', states(getCasingState()).setMinGlobalLimited(6).or(autoAbilities()))
                .where('C', heatingCoils())
                .where('#', air())
                .build();
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ICubeRenderer getBaseTexture(IMultiblockPart sourcePart) {
        return Textures.VOLTAGE_CASINGS[0];
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.MACHINE_CASING.getState(MachineCasingType.ULV);
    }

    @Override
    public SoundEvent getBreakdownSound() {
        return GTSoundEvents.BREAKDOWN_ELECTRICAL;
    }

    @SideOnly(Side.CLIENT)
    @NotNull
    @Override
    protected ICubeRenderer getFrontOverlay() {
        return Textures.PYROLYSE_OVEN_OVERLAY;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        Object type = context.get("CoilType");
        if (type instanceof IHeatingCoilBlockStats)
            this.coilTier = ((IHeatingCoilBlockStats) type).getTier();
        else
            this.coilTier = 0;
    }

    private TextFormatting getSpeedColor(int speed) {
        if (speed < 100) {
            return TextFormatting.RED;
        } else if (speed == 100) {
            return TextFormatting.GRAY;
        } else if (speed < 250) {
            return TextFormatting.GREEN;
        } else {
            return TextFormatting.LIGHT_PURPLE;
        }
    }

    @Override
    protected void configureDisplayText(MultiblockUIBuilder builder) {
        // addParallelsLine dropped: parallelLimit stays 1 (Pyrolyse Oven has no parallel support, unlike Multi
        // Smelter). addRecipeOutputLine dropped: RecipeWorkable has no getPreviousRecipe() equivalent to back it --
        // see RecipeWorkableMultiblockController's JavaDoc "Recipe-output preview line intentionally omitted".
        builder.setWorkingStatus(workable.isWorkingEnabled(), workable.isActive())
                .addEnergyUsageLine(getEnergyContainer())
                .addEnergyTierLine(GTUtility.getTierByVoltage(getEnergyContainer().getInputVoltage()))
                .addCustom((textList, syncer) -> {
                    if (!isStructureFormed()) return;
                    int tier = syncer.syncInt(coilTier);

                    int processingSpeed = tier == 0 ? 75 : 50 * (tier + 1);
                    IKey speed = KeyUtil.number(() -> getSpeedColor(processingSpeed), processingSpeed, "%");

                    IKey body = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.pyrolyse_oven.speed", speed);
                    IKey hover = KeyUtil.lang(TextFormatting.GRAY,
                            "gregtech.multiblock.pyrolyse_oven.speed_hover");
                    textList.add(KeyUtil.setHover(body, hover));
                })
                .addWorkingStatusLine()
                .addProgressLine(workable.getProgress(), workable.getMaxProgress());
    }

    @Override
    public void addInformation(ItemStack stack, @Nullable World player, List<String> tooltip, boolean advanced) {
        super.addInformation(stack, player, tooltip, advanced);
        tooltip.add(I18n.format("gregtech.machine.pyrolyse_oven.tooltip.1"));
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.coilTier = -1;
    }

    protected int getCoilTier() {
        return this.coilTier;
    }

    @Override
    public boolean hasMufflerMechanics() {
        return true;
    }

    @Override
    public boolean canBeDistinct() {
        return true;
    }
}
