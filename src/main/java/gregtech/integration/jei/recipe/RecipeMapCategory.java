package gregtech.integration.jei.recipe;

import gregtech.api.GTValues;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.mui.GTGuis;
import gregtech.api.mui.sync.GTFluidSyncHandler;
import gregtech.api.recipes.RecipeMap;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.recipes.category.GTRecipeCategory;
import gregtech.api.recipes.properties.impl.ResearchProperty;
import gregtech.api.recipes.properties.impl.ResearchPropertyData;
import gregtech.api.util.AssemblyLineManager;
import gregtech.api.util.GTUtility;
import gregtech.api.util.LocalizationUtils;
import gregtech.common.ConfigHolder;
import gregtech.common.mui.widget.GTFluidSlot;
import gregtech.integration.jei.JustEnoughItemsModule;
import gregtech.integration.jei.utils.render.FluidStackTextRenderer;
import gregtech.integration.jei.utils.render.ItemStackTextRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;
import net.minecraftforge.fluids.IFluidTank;
import net.minecraftforge.items.ItemStackHandler;

import com.cleanroommc.modularui.api.ITheme;
import com.cleanroommc.modularui.screen.GuiScreenWrapper;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.WidgetTree;
import com.cleanroommc.modularui.widget.sizer.Area;
import com.cleanroommc.modularui.widgets.slot.ItemSlot;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import mezz.jei.api.IGuiHelper;
import mezz.jei.api.gui.IDrawable;
import mezz.jei.api.gui.IGuiFluidStackGroup;
import mezz.jei.api.gui.IGuiItemStackGroup;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.ingredients.VanillaTypes;
import mezz.jei.api.recipe.IRecipeCategory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Bridges a {@link RecipeMap}'s MUI2 {@link gregtech.api.recipes.ui.RecipeMapUI} layout into a JEI recipe category.
 * <p>
 * The widget tree built by {@link gregtech.api.recipes.ui.RecipeMapUI#buildUITemplate} is attached to a headless
 * {@link ModularPanel} that is never shown on screen. It is driven through {@link ModularScreen#onResize} (wrapped
 * in a never-displayed {@link GuiScreenWrapper}) to fully resize and validate the widget tree without ever
 * rendering a frame; see the constructor for the full explanation. The resolved {@link Area}s are what get mapped
 * onto JEI's item/fluid stack groups in {@link #setRecipe}, and a lightweight walk of the tree (skipping
 * {@link ItemSlot}'s own drawing, which JEI's stack groups already handle) draws everything else into the JEI
 * recipe page in {@link #drawExtras}.
 */
public class RecipeMapCategory implements IRecipeCategory<GTRecipeWrapper> {

    private final RecipeMap<?> recipeMap;
    private final GTRecipeCategory category;
    private final ModularPanel panel;
    /**
     * Kept alive for the lifetime of this category. {@link #panel} only remains
     * {@link com.cleanroommc.modularui.api.widget.IWidget#isValid() valid} (and thus usable in
     * {@link #drawExtras}) while its owning screen is reachable and was actually resized once, see the constructor.
     */
    private final ModularScreen screen;
    private final ItemStackHandler importItems, exportItems;
    private final FluidTankList importFluids, exportFluids;
    private final IDrawable backgroundDrawable;
    private Object iconIngredient;
    private IDrawable icon;

    private static final Map<GTRecipeCategory, RecipeMapCategory> gtCategories = new Object2ObjectOpenHashMap<>();
    private static final Map<RecipeMap<?>, List<RecipeMapCategory>> recipeMapCategories = new Object2ObjectOpenHashMap<>();

    public RecipeMapCategory(@NotNull RecipeMap<?> recipeMap, @NotNull GTRecipeCategory category,
                             IGuiHelper guiHelper) {
        this.recipeMap = recipeMap;
        this.category = category;
        FluidTank[] importFluidTanks = new FluidTank[recipeMap.getMaxFluidInputs()];
        for (int i = 0; i < importFluidTanks.length; i++)
            importFluidTanks[i] = new FluidTank(16000);
        FluidTank[] exportFluidTanks = new FluidTank[recipeMap.getMaxFluidOutputs()];
        for (int i = 0; i < exportFluidTanks.length; i++)
            exportFluidTanks[i] = new FluidTank(16000);

        this.importItems = new ItemStackHandler(
                recipeMap.getMaxInputs() + (recipeMap == RecipeMaps.ASSEMBLY_LINE_RECIPES ? 1 : 0));
        this.exportItems = new ItemStackHandler(recipeMap.getMaxOutputs());
        this.importFluids = new FluidTankList(false, importFluidTanks);
        this.exportFluids = new FluidTankList(false, exportFluidTanks);

        ParentWidget<?> content = recipeMap.getRecipeMapUI().buildUITemplate(() -> 0.5,
                importItems, exportItems, importFluids, exportFluids);

        // headless panel: never shown on screen, only used to resolve widget positions/sizes for JEI.
        this.panel = GTGuis.createPanel("jei." + recipeMap.getUnlocalizedName(), 176, 166)
                .child(content);
        this.screen = new ModularScreen(GTValues.MODID, this.panel);
        // Constructing a ModularScreen alone does NOT initialise the panel: ModularPanel#onOpen (which validates
        // the widget tree) and the actual layout pass are both only triggered from onResize(), which normally runs
        // as part of the real GuiScreen lifecycle. Overlay mode (ModularScreen#constructOverlay) would let us
        // drive onResize() without a real GuiScreen, but overlays are explicitly forbidden from containing slots
        // (ItemSlot/GTFluidSlot throw in onInit() otherwise). GuiScreenWrapper is a genuine (non-overlay)
        // IMuiScreen that is never actually displayed (Minecraft.displayGuiScreen is never called on it), so it
        // lets us drive onResize() the "real" way and end up with a fully resized, valid widget tree - all
        // without ever rendering a frame. That alone isn't enough though: widgets that touch recipe-viewer settings
        // during init (e.g. GTFluidSlot#onInit) require the context to have UISettings attached first, which
        // GuiManager normally does as part of actually opening a GUI. A default UISettings has no relevant side
        // effects on its own (it just wraps a fresh RecipeViewerSettingsImpl), so attaching one here is safe.
        new GuiScreenWrapper(this.screen);
        this.screen.getContext().setSettings(new UISettings());
        this.screen.onResize(176, 166);

        // Use the panel's area (its explicit 176x166 size), not content's: content is a bare ParentWidget with no
        // explicit size of its own, so its resolved area would reflect whatever the layout engine happens to fit
        // it to rather than the actual GUI dimensions - which is what produced the wildly-off background size
        // (and consequently completely broken JEI layout) before this fix.
        Area panelArea = this.panel.getArea();
        this.backgroundDrawable = guiHelper.createBlankDrawable(panelArea.width,
                panelArea.height * 2 / 3 + recipeMap.getRecipeMapUI().getPropertyHeightShift());
        gtCategories.put(category, this);
        recipeMapCategories.compute(recipeMap, (k, v) -> {
            if (v == null) v = new ArrayList<>();
            v.add(this);
            return v;
        });
    }

    @Override
    @NotNull
    public String getUid() {
        return category.getUniqueID();
    }

    @Override
    @NotNull
    public String getTitle() {
        return LocalizationUtils.format(category.getTranslationKey());
    }

    @Nullable
    @Override
    public IDrawable getIcon() {
        if (icon != null) {
            return icon;
        } else if (iconIngredient instanceof IDrawable drawable) {
            return icon = drawable;
        } else if (iconIngredient != null) {
            // cache the icon drawable for less gc pressure
            return icon = JustEnoughItemsModule.guiHelper.createDrawableIngredient(iconIngredient);
        }
        // JEI will automatically populate the icon as the first registered catalyst if null
        return null;
    }

    public void setIcon(Object icon) {
        if (iconIngredient == null) {
            iconIngredient = icon;
        }
    }

    @Override
    @NotNull
    public String getModName() {
        return GTValues.MODID;
    }

    @Override
    @NotNull
    public IDrawable getBackground() {
        return backgroundDrawable;
    }

    /**
     * @return the widget's area, relative to this category's panel origin (i.e. relative to the JEI recipe page).
     */
    private Area relativeArea(@NotNull Area widgetArea) {
        Area panelArea = panel.getArea();
        Area relative = new Area();
        relative.x = widgetArea.x - panelArea.x;
        relative.y = widgetArea.y - panelArea.y;
        relative.width = widgetArea.width;
        relative.height = widgetArea.height;
        return relative;
    }

    @Override
    public void setRecipe(IRecipeLayout recipeLayout, @NotNull GTRecipeWrapper recipeWrapper,
                          @NotNull IIngredients ingredients) {
        IGuiItemStackGroup itemStackGroup = recipeLayout.getItemStacks();
        IGuiFluidStackGroup fluidStackGroup = recipeLayout.getFluidStacks();

        WidgetTree.foreachChildBFS(panel, widget -> {
            if (widget instanceof ItemSlot slot) {
                var syncSlot = slot.getSyncHandler().getSlot();
                Area area = relativeArea(slot.getArea());
                if (syncSlot.getItemHandler() == importItems) {
                    int index = syncSlot.getSlotIndex();
                    // this is input item stack slot widget, so add it to item group
                    itemStackGroup.init(index, true,
                            new ItemStackTextRenderer(recipeWrapper.isNotConsumedItem(index)),
                            area.x + 1, area.y + 1, area.width - 2, area.height - 2, 0, 0);
                } else if (syncSlot.getItemHandler() == exportItems) {
                    int index = syncSlot.getSlotIndex();
                    // this is output item stack slot widget, so add it to item group
                    itemStackGroup.init(importItems.getSlots() + index, false,
                            new ItemStackTextRenderer(
                                    recipeWrapper.getOutputChance(
                                            index - recipeWrapper.getRecipe().getOutputs().size()),
                                    recipeWrapper.getChancedOutputLogic()),
                            area.x + 1, area.y + 1, area.width - 2, area.height - 2, 0, 0);
                }
            } else if (widget instanceof GTFluidSlot slot) {
                GTFluidSyncHandler syncHandler = slot.getSyncHandler();
                if (syncHandler == null) return true;
                IFluidTank tank = syncHandler.getTank();
                Area area = relativeArea(slot.getArea());
                int fluidRenderOffset = 1;
                if (importFluids.getFluidTanks().contains(tank)) {
                    int importIndex = importFluids.getFluidTanks().indexOf(tank);
                    List<List<FluidStack>> inputsList = ingredients.getInputs(VanillaTypes.FLUID);
                    int fluidAmount = 0;
                    if (inputsList.size() > importIndex && !inputsList.get(importIndex).isEmpty())
                        fluidAmount = inputsList.get(importIndex).get(0).amount;
                    // this is input tank widget, so add it to fluid group
                    fluidStackGroup.init(importIndex, true,
                            new FluidStackTextRenderer(fluidAmount, false,
                                    area.width - (2 * fluidRenderOffset),
                                    area.height - (2 * fluidRenderOffset), null)
                                            .setNotConsumed(recipeWrapper.isNotConsumedFluid(importIndex)),
                            area.x + fluidRenderOffset, area.y + fluidRenderOffset,
                            area.width - (2 * fluidRenderOffset), area.height - (2 * fluidRenderOffset), 0, 0);
                } else if (exportFluids.getFluidTanks().contains(tank)) {
                    int exportIndex = exportFluids.getFluidTanks().indexOf(tank);
                    List<List<FluidStack>> inputsList = ingredients.getOutputs(VanillaTypes.FLUID);
                    int fluidAmount = 0;
                    if (inputsList.size() > exportIndex && !inputsList.get(exportIndex).isEmpty())
                        fluidAmount = inputsList.get(exportIndex).get(0).amount;
                    // this is output tank widget, so add it to fluid group
                    fluidStackGroup.init(importFluids.getFluidTanks().size() + exportIndex, false,
                            new FluidStackTextRenderer(fluidAmount, false,
                                    area.width - (2 * fluidRenderOffset),
                                    area.height - (2 * fluidRenderOffset), null,
                                    recipeWrapper.getFluidOutputChance(
                                            exportIndex - recipeWrapper.getRecipe().getFluidOutputs().size()),
                                    recipeWrapper.getChancedFluidOutputLogic()),
                            area.x + fluidRenderOffset, area.y + fluidRenderOffset,
                            area.width - (2 * fluidRenderOffset), area.height - (2 * fluidRenderOffset), 0, 0);
                }
            }
            return true;
        }, true);

        if (ConfigHolder.machines.enableResearch && this.recipeMap == RecipeMaps.ASSEMBLY_LINE_RECIPES) {
            ResearchPropertyData data = recipeWrapper.getRecipe().getProperty(ResearchProperty.getInstance(), null);
            if (data != null) {
                List<ItemStack> dataItems = new ArrayList<>();
                for (ResearchPropertyData.ResearchEntry entry : data) {
                    ItemStack dataStick = entry.dataItem().copy();
                    AssemblyLineManager.writeResearchToNBT(GTUtility.getOrCreateNbtCompound(dataStick),
                            entry.researchId());
                    dataItems.add(dataStick);
                }
                itemStackGroup.set(16, dataItems);
            }
        }

        itemStackGroup.addTooltipCallback(recipeWrapper::addItemTooltip);
        fluidStackGroup.addTooltipCallback(recipeWrapper::addFluidTooltip);
        itemStackGroup.set(ingredients);
        fluidStackGroup.set(ingredients);
    }

    @Override
    public void drawExtras(@NotNull Minecraft minecraft) {
        // We deliberately don't use WidgetTree.drawTree here: ItemSlot#draw() (drawSlot) requires the widget's
        // screen to be backed by a real, currently-displayed GuiContainer, since it reads live drag/cursor state
        // (clicked slot, dragged stack, etc.) from it - none of which exists or makes sense for this headless,
        // never-shown panel. Item stacks are instead drawn by JEI's own IGuiItemStackGroup (see setRecipe), so we
        // walk the tree ourselves and skip ItemSlot's draw() call while still drawing everything else normally
        // (slot background/overlay textures, GTFluidSlot - which has no such GuiContainer dependency -,
        // ProgressWidget, etc.).
        ModularGuiContext context = panel.getContext();
        ITheme theme = panel.getTheme();
        WidgetTree.foreachChildBFS(panel, widget -> {
            if (!widget.isEnabled()) return true;
            Area area = relativeArea(widget.getArea());
            WidgetThemeEntry<?> widgetTheme = widget.getWidgetTheme(theme);
            GlStateManager.pushMatrix();
            GlStateManager.translate(area.x, area.y, 0);
            widget.drawBackground(context, widgetTheme);
            if (!(widget instanceof ItemSlot)) {
                widget.draw(context, widgetTheme);
            }
            widget.drawOverlay(context, widgetTheme);
            GlStateManager.popMatrix();
            return true;
        }, false);
    }

    @Nullable
    public static RecipeMapCategory getCategoryFor(@NotNull GTRecipeCategory category) {
        return gtCategories.get(category);
    }

    @Nullable
    public static Collection<RecipeMapCategory> getCategoriesFor(@NotNull RecipeMap<?> recipeMap) {
        return recipeMapCategories.get(recipeMap);
    }
}
