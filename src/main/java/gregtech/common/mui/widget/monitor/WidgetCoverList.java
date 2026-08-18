package gregtech.common.mui.widget.monitor;

import gregtech.common.covers.CoverDigitalInterface;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.value.sync.IntSyncValue;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.ListWidget;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Scrollable list of {@link CoverDigitalInterface}s a monitor screen can bind to. Clicking a row's icon closes the
 * GUI and highlights the covered block in-world (without changing the selection); clicking elsewhere in the row
 * toggles that cover as the current selection, invoking {@code onSelected} (with {@code null} on deselect).
 */
public class WidgetCoverList extends ListWidget<IWidget, WidgetCoverList> {

    public WidgetCoverList(@NotNull PanelSyncManager syncManager, @NotNull String syncKey,
                           @NotNull List<CoverDigitalInterface> covers, int entryWidth,
                           @NotNull Supplier<CoverDigitalInterface> selectedSupplier,
                           @NotNull Consumer<CoverDigitalInterface> onSelected) {
        IntSyncValue selectedIndexSync = new IntSyncValue(
                () -> covers.indexOf(selectedSupplier.get()),
                index -> onSelected.accept(index < 0 || index >= covers.size() ? null : covers.get(index)));
        syncManager.syncValue(syncKey, selectedIndexSync);

        for (int i = 0; i < covers.size(); i++) {
            CoverListEntry entry = CoverListEntry.create(covers.get(i), i, selectedIndexSync);
            if (entry != null) {
                // ListWidget doesn't stretch its children to its own width automatically - each row must be
                // sized explicitly, otherwise its clickable area ends up degenerate (see WidgetOreList).
                child(entry.width(entryWidth));
            }
        }
    }
}
