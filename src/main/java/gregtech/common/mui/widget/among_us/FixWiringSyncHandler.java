package gregtech.common.mui.widget.among_us;

import gregtech.api.GTValues;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.value.sync.SyncHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Server-authoritative state for the April Fools' "fix wiring" minigame ({@link FixWiringTaskWidget}). <br>
 * The wire colors and the shuffled left-to-right connection mapping are rolled once on the server and pushed to
 * the client on initial sync; the client then reports each completed connection back to the server, which fires
 * {@link #onFinished} once all four wires are connected.
 */
public class FixWiringSyncHandler extends SyncHandler {

    private static final int SYNC_INIT_STATE = 0;
    private static final int CONNECT_WIRE = 1;

    private final int[] colors = new int[4];
    private final int[] init = new int[4];
    private final boolean[] connect = new boolean[4];
    private Runnable onFinished;
    private boolean initialized;

    public FixWiringSyncHandler() {
        for (int i = 0; i < 4; i++) {
            colors[i] = (GTValues.RNG.nextInt() & 0x00ffffff) | 0xff000000;
        }
        List<Integer> list = new ArrayList<>(Arrays.asList(0, 1, 2, 3));
        Collections.shuffle(list, GTValues.RNG);
        for (int i = 0; i < list.size(); i++) {
            init[i] = list.get(i);
        }
    }

    public FixWiringSyncHandler setOnFinished(Runnable onFinished) {
        this.onFinished = onFinished;
        return this;
    }

    @Override
    public void detectAndSendChanges(boolean init) {
        if (init) {
            // roll happened in the constructor on both sides already, but only the server's roll is authoritative
            syncToClient(SYNC_INIT_STATE, buf -> {
                for (int color : colors) buf.writeInt(color);
                for (int target : this.init) buf.writeVarInt(target);
                for (boolean connected : connect) buf.writeBoolean(connected);
            });
        }
    }

    @Override
    public void readOnClient(int id, PacketBuffer buf) {
        if (id == SYNC_INIT_STATE) {
            for (int i = 0; i < 4; i++) colors[i] = buf.readInt();
            for (int i = 0; i < 4; i++) init[i] = buf.readVarInt();
            for (int i = 0; i < 4; i++) connect[i] = buf.readBoolean();
            initialized = true;
        }
    }

    @Override
    public void readOnServer(int id, PacketBuffer buf) {
        if (id == CONNECT_WIRE) {
            int idx = buf.readVarInt();
            if (idx < 0 || idx >= 4) return;
            connect[idx] = true;
            if (connect[0] && connect[1] && connect[2] && connect[3] && onFinished != null) {
                onFinished.run();
            }
        }
    }

    /**
     * Called on the client when the player successfully drags a wire to its matching terminal. Updates the local
     * state immediately for responsiveness and notifies the server, which is the authority on whether the
     * minigame is complete.
     */
    public void connectWire(int idx) {
        connect[idx] = true;
        syncToServer(CONNECT_WIRE, buf -> buf.writeVarInt(idx));
    }

    /**
     * @return false for the single frame between the widget opening and the initial state packet arriving from
     *         the server; draw calls should no-op until this is true.
     */
    public boolean isInitialized() {
        return initialized;
    }

    public int getColor(int idx) {
        return colors[idx];
    }

    /**
     * @return the index of the right-side terminal that the left-side wire {@code idx} must be connected to.
     */
    public int getTarget(int idx) {
        return init[idx];
    }

    public boolean isConnected(int idx) {
        return connect[idx];
    }
}
