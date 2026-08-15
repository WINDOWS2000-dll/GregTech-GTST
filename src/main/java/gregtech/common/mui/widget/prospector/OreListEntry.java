package gregtech.common.mui.widget.prospector;

import gregtech.api.mui.GTGuiTextures;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.stack.MaterialStack;
import gregtech.client.utils.RenderUtil;
import gregtech.common.mui.sync.prospector.ProspectingSyncHandler;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.screen.viewport.ModularGuiContext;
import com.cleanroommc.modularui.theme.WidgetThemeEntry;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A single row of the prospector's ore/fluid list, showing a representative icon (rotating through every match for
 * item ore dictionary entries), the localized name, and letting the player click it to filter the map to that entry.
 */
public class OreListEntry extends ParentWidget<OreListEntry> implements Interactable {

    private static final IDrawable SELECTED_HIGHLIGHT = new Rectangle().color(0x4BFFFFFF);
    private static final int ICON_ROTATE_TICKS = 20;

    private final ProspectingSyncHandler syncHandler;
    private final String key;
    private final boolean isSelectAllEntry;
    private final List<ItemStack> icons;
    private final FluidStack fluid;

    private int tickCounter;

    public OreListEntry(@NotNull ProspectingSyncHandler syncHandler, @NotNull String key, @NotNull String displayName,
                        @NotNull Supplier<String> searchQuery) {
        this.syncHandler = syncHandler;
        this.key = key;
        this.isSelectAllEntry = ProspectingTexture.SELECTED_ALL.equals(key);

        if (this.isSelectAllEntry) {
            this.icons = Collections.emptyList();
            this.fluid = null;
            child(new Widget<>().size(16).pos(1, 1).background(GTGuiTextures.RECIPE_LOCK));
        } else if (syncHandler.getMode() == ProspectorMode.FLUID) {
            this.icons = Collections.emptyList();
            this.fluid = FluidRegistry.getFluidStack(key, 1);
        } else {
            this.icons = OreDictUnifier.getAllWithOreDictionaryName(key);
            this.fluid = null;
        }

        height(18);
        overlay(new DynamicDrawable(() -> syncHandler.getSelected().equals(key) ? SELECTED_HIGHLIGHT : IDrawable.NONE));
        setEnabledIf(w -> this.isSelectAllEntry || matchesSearch(displayName, searchQuery.get()));
        child(IKey.str(displayName).color(labelColor()).asWidget().pos(20, 5));
    }

    private static boolean matchesSearch(String displayName, String query) {
        if (query.isEmpty()) return true;
        String lowerName = displayName.toLowerCase(Locale.ROOT);
        return lowerName.contains(query) || query.contains(lowerName);
    }

    private int labelColor() {
        if (this.isSelectAllEntry) return -1;
        if (this.fluid != null) {
            return WidgetOreList.getFluidColor(this.fluid.getFluid());
        }
        MaterialStack materialStack = OreDictUnifier.getMaterial(OreDictUnifier.get(this.key));
        return materialStack == null ? this.key.hashCode() : materialStack.material.getMaterialRGB() | 0xFF000000;
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        this.tickCounter++;
    }

    @Override
    public void draw(ModularGuiContext context, WidgetThemeEntry<?> widgetTheme) {
        if (this.fluid != null) {
            RenderUtil.drawFluidForGui(this.fluid, 1, 1, 1, 16, 16);
        } else if (!this.icons.isEmpty()) {
            int index = this.tickCounter / ICON_ROTATE_TICKS % this.icons.size();
            RenderUtil.drawItemStack(this.icons.get(index), 1, 1, false);
        }
    }

    @NotNull
    @Override
    public Result onMousePressed(int mouseButton) {
        this.syncHandler.select(this.key);
        return Result.SUCCESS;
    }
}
