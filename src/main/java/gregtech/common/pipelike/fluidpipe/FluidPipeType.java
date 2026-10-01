package gregtech.common.pipelike.fluidpipe;

import gregtech.api.fluids.attribute.FluidAttribute;
import gregtech.api.pipenet.block.material.IMaterialPipeType;
import gregtech.api.unification.material.properties.FluidPipeProperties;
import gregtech.api.unification.ore.OrePrefix;

import org.jetbrains.annotations.NotNull;

public enum FluidPipeType implements IMaterialPipeType<FluidPipeProperties> {

    TINY("tiny", 0.25f, 1, OrePrefix.pipeTinyFluid, true),
    SMALL("small", 0.375f, 2, OrePrefix.pipeSmallFluid, true),
    NORMAL("normal", 0.5f, 6, OrePrefix.pipeNormalFluid, true),
    LARGE("large", 0.75f, 12, OrePrefix.pipeLargeFluid, true),
    HUGE("huge", 0.875f, 24, OrePrefix.pipeHugeFluid, true),

    QUADRUPLE("quadruple", 0.95f, 2, OrePrefix.pipeQuadrupleFluid, true, 4),
    NONUPLE("nonuple", 0.95f, 2, OrePrefix.pipeNonupleFluid, true, 9);

    public static final FluidPipeType[] VALUES = values();

    public final String name;
    public final float thickness;
    public final int capacityMultiplier;
    public final OrePrefix orePrefix;
    public final boolean opaque;
    public final int channels;

    FluidPipeType(String name, float thickness, int capacityMultiplier, OrePrefix orePrefix, boolean opaque) {
        this(name, thickness, capacityMultiplier, orePrefix, opaque, 1);
    }

    FluidPipeType(String name, float thickness, int capacityMultiplier, OrePrefix orePrefix, boolean opaque,
                  int channels) {
        this.name = name;
        this.thickness = thickness;
        this.capacityMultiplier = capacityMultiplier;
        this.orePrefix = orePrefix;
        this.opaque = opaque;
        this.channels = channels;
    }

    @NotNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public float getThickness() {
        return thickness;
    }

    @Override
    public OrePrefix getOrePrefix() {
        return orePrefix;
    }

    @Override
    public FluidPipeProperties modifyProperties(FluidPipeProperties baseProperties) {
        FluidPipeProperties properties = new FluidPipeProperties(
                baseProperties.getMaxFluidTemperature(),
                baseProperties.getThroughput() * capacityMultiplier,
                baseProperties.isGasProof(),
                baseProperties.isAcidProof(),
                baseProperties.isCryoProof(),
                baseProperties.isPlasmaProof(),
                channels);
        // The constructor above only threads through the handful of attributes with a dedicated boolean parameter
        // (acidProof -> FluidAttributes.ACID); any other FluidAttribute a material's own properties were marked
        // to contain via setCanContain (e.g. an addon's custom coolant/reagent attribute) would otherwise be
        // silently dropped here, since per-size pipe properties are a fresh object, not the same instance the
        // material's own setCanContain calls mutated -- causing every actual placed pipe block to treat that
        // fluid as unsupported (corroding/destroying the pipe on contact) regardless of what the material was
        // configured to allow.
        for (FluidAttribute attribute : baseProperties.getContainedAttributes()) {
            properties.setCanContain(attribute, true);
        }
        return properties;
    }

    @Override
    public boolean isPaintable() {
        return true;
    }
}
