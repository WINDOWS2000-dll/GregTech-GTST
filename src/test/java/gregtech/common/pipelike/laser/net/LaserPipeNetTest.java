package gregtech.common.pipelike.laser.net;

import net.minecraft.util.math.BlockPos;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaserPipeNetTest {

    private LaserPipeNet net;
    private Field netDataField;

    @BeforeEach
    void setUp() throws Exception {
        net = new LaserPipeNet(null);
        netDataField = LaserPipeNet.class.getDeclaredField("netData");
        netDataField.setAccessible(true);
    }

    @SuppressWarnings("unchecked")
    private Long2ObjectMap<LaserRoutePath> netData() throws Exception {
        return (Long2ObjectMap<LaserRoutePath>) netDataField.get(net);
    }

    private static LaserRoutePath routeThrough(BlockPos... positions) {
        LongSet path = new LongOpenHashSet();
        for (BlockPos pos : positions) {
            path.add(pos.toLong());
        }
        return new LaserRoutePath(null, null, positions.length, path);
    }

    @Test
    void onPipeConnectionsUpdateInvalidatesOnlyRoutesThroughThatPosition() throws Exception {
        BlockPos source1 = new BlockPos(0, 0, 0);
        BlockPos onSource1Route = new BlockPos(2, 0, 0);
        BlockPos source2 = new BlockPos(10, 0, 0);
        BlockPos onSource2Route = new BlockPos(20, 0, 0);

        netData().put(source1.toLong(), routeThrough(source1, onSource1Route, new BlockPos(3, 0, 0)));
        netData().put(source2.toLong(), routeThrough(source2, onSource2Route));

        net.onPipeConnectionsUpdate(onSource1Route);

        assertFalse(netData().containsKey(source1.toLong()),
                "the route that actually passed through the changed position must be invalidated");
        assertTrue(netData().containsKey(source2.toLong()),
                "a route unrelated to the changed position must survive");
    }

    @Test
    void onChunkUnloadInvalidatesOnlyRoutesThroughThatPosition() throws Exception {
        BlockPos source1 = new BlockPos(0, 0, 0);
        BlockPos unloadedPos = new BlockPos(1, 0, 0);
        BlockPos source2 = new BlockPos(50, 0, 0);

        netData().put(source1.toLong(), routeThrough(source1, unloadedPos));
        netData().put(source2.toLong(), routeThrough(source2, new BlockPos(51, 0, 0)));

        net.onChunkUnload(unloadedPos);

        assertFalse(netData().containsKey(source1.toLong()));
        assertTrue(netData().containsKey(source2.toLong()));
    }

    @Test
    void cachedFailureIsAlwaysInvalidatedConservatively() throws Exception {
        BlockPos source = new BlockPos(0, 0, 0);
        netData().put(source.toLong(), null);

        net.onPipeConnectionsUpdate(new BlockPos(999, 0, 0));

        assertFalse(netData().containsKey(source.toLong()));
    }
}
