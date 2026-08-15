package gregtech.common.mui.widget.prospector;

import gregtech.common.mui.sync.prospector.ProspectingSyncHandler;

import net.minecraft.client.resources.I18n;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.widgets.ListWidget;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;

/**
 * Scrollable list of every ore/fluid the holder's {@link ProspectingSyncHandler} has discovered so far, sorted
 * alphabetically and filterable by {@link #setSearchQuery(String)}. Clicking an entry filters the map to it.
 */
public class WidgetOreList extends ListWidget<IWidget, WidgetOreList> {

    private final ProspectingSyncHandler syncHandler;
    private final int entryWidth;

    private String searchQuery = "";
    private int knownOreCount = -1;

    public WidgetOreList(@NotNull ProspectingSyncHandler syncHandler, int entryWidth) {
        this.syncHandler = syncHandler;
        this.entryWidth = entryWidth;
    }

    @Override
    public void onInit() {
        super.onInit();
        rebuildEntries();
    }

    @Override
    public void onUpdate() {
        super.onUpdate();
        if (this.syncHandler.getOres().size() != this.knownOreCount) {
            rebuildEntries();
        }
    }

    public void setSearchQuery(@NotNull String query) {
        this.searchQuery = query.toLowerCase(Locale.ROOT);
    }

    private void rebuildEntries() {
        Map<String, String> ores = this.syncHandler.getOres();
        this.knownOreCount = ores.size();

        removeAll();
        child(entry(ProspectingTexture.SELECTED_ALL, I18n.format("terminal.prospector.list")));
        ores.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getValue))
                .forEach(entry -> child(entry(entry.getKey(), entry.getValue())));
    }

    private OreListEntry entry(String key, String displayName) {
        return new OreListEntry(this.syncHandler, key, displayName, () -> this.searchQuery)
                .width(this.entryWidth);
    }

    public static int getFluidColor(Fluid fluid) {
        if (fluid == FluidRegistry.WATER) return 3183823;
        if (fluid == FluidRegistry.LAVA) return 16766720;
        return fluid.getColor();
    }
}
