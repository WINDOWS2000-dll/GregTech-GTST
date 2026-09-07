package gregtech.integration.crafttweaker.recipe;

import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.logic.statemachine.property.RecipePropertySet;
import gregtech.api.recipes.roll.OverclockRollBoost;
import gregtech.api.util.GTUtility;

import crafttweaker.annotations.ZenRegister;
import crafttweaker.api.item.IItemStack;
import crafttweaker.api.liquid.ILiquidStack;
import crafttweaker.api.minecraft.CraftTweakerMC;
import crafttweaker.mc1120.item.MCItemStack;
import crafttweaker.mc1120.liquid.MCLiquidStack;
import stanhebben.zenscript.annotations.Optional;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenGetter;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@ZenClass("mods.gregtech.recipe.Recipe")
@ZenRegister
@SuppressWarnings("unused")
public class CTRecipe {

    // TODO YEET

    private final RecipeMap<?> recipeMap;
    private final Recipe backingRecipe;

    public CTRecipe(RecipeMap<?> recipeMap, Recipe backingRecipe) {
        this.recipeMap = recipeMap;
        this.backingRecipe = backingRecipe;
    }

    @ZenGetter("inputs")
    public List<InputIngredient> getInputs() {
        return this.backingRecipe.getInputs().stream()
                .map(InputIngredient::new)
                .collect(Collectors.toList());
    }

    @ZenGetter("outputs")
    public List<IItemStack> getOutputs() {
        return this.backingRecipe.getGuaranteedItemOutputs().stream()
                .map(MCItemStack::new)
                .collect(Collectors.toList());
    }

    @ZenMethod
    public List<IItemStack> getResultItemOutputs(@Optional(valueLong = 1) int tier) {
        int recipeTier = GTUtility.getTierByVoltage(getEUt());
        int boostStrength = OverclockRollBoost.boostStrength(recipeMap.getChanceFunction(), recipeTier, tier);
        return this.backingRecipe.getItemOutputProvider()
                .computeOutputsWithBoost(Collections.emptyList(), Collections.emptyList(), RecipePropertySet.empty(),
                        boostStrength, 1, Integer.MAX_VALUE)
                .stream()
                .map(MCItemStack::new)
                .collect(Collectors.toList());
    }

    @ZenGetter("fluidInputs")
    public List<ILiquidStack> getFluidInputs() {
        return this.backingRecipe.getFluidInputs().stream()
                .map(fi -> new MCLiquidStack(fi.getInputFluidStack()))
                .collect(Collectors.toList());
    }

    @ZenMethod
    public boolean hasInputFluid(ILiquidStack liquidStack) {
        return this.backingRecipe.hasInputFluid(CraftTweakerMC.getLiquidStack(liquidStack));
    }

    @ZenGetter("fluidOutputs")
    public List<ILiquidStack> getFluidOutputs() {
        return this.backingRecipe.getGuaranteedFluidOutputs().stream()
                .map(MCLiquidStack::new)
                .collect(Collectors.toList());
    }

    @ZenGetter("duration")
    public int getDuration() {
        return this.backingRecipe.getDuration();
    }

    @ZenGetter("EUt")
    public long getEUt() {
        return this.backingRecipe.getEUt();
    }

    @ZenGetter("hidden")
    public boolean isHidden() {
        return this.backingRecipe.isHidden();
    }

    @ZenMethod
    public boolean remove() {
        return this.recipeMap.removeRecipe(this.backingRecipe);
    }
}
