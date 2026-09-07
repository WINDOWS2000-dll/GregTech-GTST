package gregtech.api.metatileentity;

import gregtech.Bootstrap;
import gregtech.api.metatileentity.interfaces.IGregTechTileEntity;
import gregtech.api.util.GTUtility;
import gregtech.api.util.world.DummyWorld;

import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdjacentBlockGateTest {

    @BeforeAll
    static void bootstrap() {
        Bootstrap.perform();
    }

    private static int testId = 20200;
    private static final BlockPos POS = BlockPos.ORIGIN;

    private static class TestMachine extends MetaTileEntity {

        TestMachine(ResourceLocation id) {
            super(id);
        }

        @Override
        public MetaTileEntity createMetaTileEntity(IGregTechTileEntity tileEntity) {
            return new TestMachine(metaTileEntityId);
        }
    }

    private static MetaTileEntity newHost(EnumFacing frontFacing) {
        MetaTileEntity mte = new TestMachine(GTUtility.gregtechId("adjacent_block_gate_test_" + testId++));
        MetaTileEntity holder = new MetaTileEntityHolder().setMetaTileEntity(mte);
        MetaTileEntityHolder tileEntityHolder = (MetaTileEntityHolder) holder.getHolder();
        tileEntityHolder.setWorld(DummyWorld.INSTANCE);
        tileEntityHolder.setPos(POS);
        holder.setFrontFacing(frontFacing);
        return holder;
    }

    /** Mirrors MetaTileEntityRockBreaker's exact requirement: lava and water, excluding front facing/vertical sides. */
    private static AdjacentBlockGate rockBreakerGate(MetaTileEntity host) {
        Predicate<EnumFacing> sideFilter = side -> side != host.getFrontFacing() && !side.getAxis().isVertical();
        Predicate<IBlockState> isLava = state -> state.getBlock() == Blocks.LAVA ||
                state.getBlock() == Blocks.FLOWING_LAVA;
        Predicate<IBlockState> isWater = state -> state.getBlock() == Blocks.WATER ||
                state.getBlock() == Blocks.FLOWING_WATER;
        List<Predicate<IBlockState>> categories = Arrays.asList(isLava, isWater);
        return new AdjacentBlockGate(host, sideFilter, categories);
    }

    @AfterEach
    void clearNeighbors() {
        for (EnumFacing side : EnumFacing.VALUES) {
            DummyWorld.INSTANCE.setBlockState(POS.offset(side), Blocks.AIR.getDefaultState());
        }
    }

    @Test
    void notYetPlacedDefaultsToSatisfied() {
        // world == null before the host is placed; matches the legacy MetaTileEntityRockBreaker default.
        TestMachine bare = new TestMachine(GTUtility.gregtechId("adjacent_block_gate_test_bare_" + testId++));
        AdjacentBlockGate gate = rockBreakerGate(bare);

        gate.recompute();

        assertTrue(gate.isSatisfied());
    }

    @Test
    void bothCategoriesPresentOnQualifyingSidesSatisfiesTheGate() {
        MetaTileEntity host = newHost(EnumFacing.NORTH);
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.SOUTH), Blocks.LAVA.getDefaultState());
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.EAST), Blocks.WATER.getDefaultState());
        AdjacentBlockGate gate = rockBreakerGate(host);

        gate.recompute();

        assertTrue(gate.isSatisfied());
    }

    @Test
    void onlyOneCategoryPresentLeavesTheGateUnsatisfied() {
        MetaTileEntity host = newHost(EnumFacing.NORTH);
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.SOUTH), Blocks.LAVA.getDefaultState());
        // no water anywhere
        AdjacentBlockGate gate = rockBreakerGate(host);

        gate.recompute();

        assertFalse(gate.isSatisfied());
    }

    @Test
    void aQualifyingBlockOnTheFrontFacingSideDoesNotCount() {
        MetaTileEntity host = newHost(EnumFacing.NORTH);
        // both categories present, but only on the excluded front-facing/vertical sides
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.NORTH), Blocks.LAVA.getDefaultState()); // front facing
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.UP), Blocks.WATER.getDefaultState()); // vertical
        AdjacentBlockGate gate = rockBreakerGate(host);

        gate.recompute();

        assertFalse(gate.isSatisfied());
    }

    @Test
    void resultIsCachedUntilRecomputeIsCalledAgain() {
        MetaTileEntity host = newHost(EnumFacing.NORTH);
        AdjacentBlockGate gate = rockBreakerGate(host);
        gate.recompute();
        assertFalse(gate.isSatisfied());

        // change the world without calling recompute(): the cached value must not change on its own
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.SOUTH), Blocks.LAVA.getDefaultState());
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.EAST), Blocks.WATER.getDefaultState());
        assertFalse(gate.isSatisfied());

        gate.recompute();
        assertTrue(gate.isSatisfied());
    }

    @Test
    void nbtRoundTripPreservesTheCachedResult() {
        MetaTileEntity host = newHost(EnumFacing.NORTH);
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.SOUTH), Blocks.LAVA.getDefaultState());
        DummyWorld.INSTANCE.setBlockState(POS.offset(EnumFacing.EAST), Blocks.WATER.getDefaultState());
        AdjacentBlockGate gate = rockBreakerGate(host);
        gate.recompute();

        net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
        gate.writeToNBT(data, "hasValidFluids");

        AdjacentBlockGate restored = rockBreakerGate(host);
        restored.readFromNBT(data, "hasValidFluids");

        assertTrue(restored.isSatisfied());
    }
}
