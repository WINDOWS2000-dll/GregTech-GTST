package gregtech.common.pipelike.itempipe.net;

import gregtech.api.util.FacingPos;

import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Regression coverage for a self-audit finding: {@link ItemPipeNet#NET_DATA} must be keyed by {@code
 * (pipePos, facing)}, not {@code pipePos} alone -- {@code TileEntityItemPipe} exposes one independent {@code
 * ItemNetHandler} per face, and the computed route list actually depends on which face is asking (see {@link
 * ItemPipeNet#NET_DATA}'s own note: {@link ItemNetWalker} excludes whichever neighbour lies back through the
 * calling face itself, to avoid immediately routing an inserted item back out the side it just came in from).
 * A junction pipe fed from two different faces at once would otherwise have the second face silently reuse the
 * first face's cache entry, computed with the wrong exclusion.
 * <p>
 * This populates the cache directly (bypassing {@link ItemNetWalker}, which needs a real world) to isolate and
 * verify just the cache-key behaviour.
 */
class ItemPipeNetTest {

    @SuppressWarnings("unchecked")
    private static Map<FacingPos, List<ItemRoutePath>> netDataOf(ItemPipeNet net) throws Exception {
        Field field = ItemPipeNet.class.getDeclaredField("NET_DATA");
        field.setAccessible(true);
        return (Map<FacingPos, List<ItemRoutePath>>) field.get(net);
    }

    @Test
    void differentFacingsAtTheSamePipePosDoNotShareACacheEntry() throws Exception {
        ItemPipeNet net = new ItemPipeNet(null);
        Map<FacingPos, List<ItemRoutePath>> netData = netDataOf(net);

        BlockPos pipePos = new BlockPos(0, 0, 0);
        List<ItemRoutePath> northRoute = Collections
                .singletonList(new ItemRoutePath(null, null, 1, null, Collections.emptyList()));
        List<ItemRoutePath> southRoute = Collections
                .singletonList(new ItemRoutePath(null, null, 2, null, Collections.emptyList()));
        netData.put(new FacingPos(pipePos, EnumFacing.NORTH), northRoute);
        netData.put(new FacingPos(pipePos, EnumFacing.SOUTH), southRoute);

        assertSame(northRoute, net.getNetData(pipePos, EnumFacing.NORTH),
                "a face's own cached route list must be returned for that face");
        assertSame(southRoute, net.getNetData(pipePos, EnumFacing.SOUTH),
                "a different face at the same pipePos must not reuse another face's cached route list");
        assertNotSame(net.getNetData(pipePos, EnumFacing.NORTH), net.getNetData(pipePos, EnumFacing.SOUTH));
    }
}
