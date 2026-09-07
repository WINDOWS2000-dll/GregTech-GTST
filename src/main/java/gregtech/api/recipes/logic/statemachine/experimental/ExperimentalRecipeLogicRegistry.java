package gregtech.api.recipes.logic.statemachine.experimental;

import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.RecipeWorkableGeneratorMetaTileEntity;
import gregtech.api.metatileentity.RecipeWorkableSimpleMachineMetaTileEntity;
import gregtech.api.metatileentity.RecipeWorkableSimpleMachineMetaTileEntityResizable;
import gregtech.api.metatileentity.RecipeWorkableTieredMetaTileEntity;
import gregtech.api.metatileentity.SteamMetaTileEntity;
import gregtech.api.metatileentity.multiblock.RecipeWorkableMultiblockController;
import gregtech.api.metatileentity.multiblock.RecipeWorkablePrimitiveMultiblockController;
import gregtech.api.metatileentity.multiblock.RecipeWorkableSteamMultiblockController;
import gregtech.api.statemachine.GTStateMachineBuilder;
import gregtech.common.ConfigHolder;

import net.minecraftforge.fml.common.Loader;

import com.github.bsideup.jabel.Desugar;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.apache.commons.lang3.ArrayUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A global, static, machine-type-keyed registry through which an addon can inject either per-instance data
 * ({@link #putExtensionFactory}) or additional {@link GTStateMachineBuilder} graph nodes
 * ({@link #addPostProcessor}) into GTST's own StateMachine-driven machines &mdash; without touching GTST's source.
 * <p>
 * <b>This entire package is explicitly outside GTST's normal compatibility contract.</b> See the package's own
 * JavaDoc for what that means. Nothing here is guaranteed to exist, keep this exact shape, or keep working the same
 * way from one GTST version to the next.
 * <p>
 * <b>Why a global registry keyed by machine type, instead of writing directly onto a constructed instance:</b> a
 * machine's {@link gregtech.api.recipes.logic.statemachine.RecipeLogicConfig} (and the {@code GTStateMachine} graph
 * built from it) is fully assembled <i>during</i> that machine's own constructor, by
 * {@code RecipeLogicGraphBuilder.build}. There is no point at which an addon could obtain a reference to that
 * specific, not-yet-fully-constructed instance and inject something into it in time. Registering ahead of time,
 * during mod loading, against the machine's {@code Class} rather than a particular instance, sidesteps the problem
 * entirely: by the time any machine is actually constructed, every registration any addon will ever make has
 * already happened.
 * <p>
 * <b>Why registration freezes at first actual machine construction, not at any particular FML lifecycle event:</b>
 * an event such as {@code FMLLoadCompleteEvent} was considered and rejected. Forge processes mods in a consistent
 * relative order &mdash; determined by each mod's {@code @Mod(dependencies = ...)} declaration &mdash; across
 * <i>every</i> lifecycle phase. An addon that correctly declares {@code "after:gregtech"} is therefore processed
 * after GTST in preInit, in init, in postInit, <i>and</i> in loadComplete. Freezing this registry at any of those
 * events would systematically reject every addon that depends on GTST the way it's supposed to. No actual machine
 * is ever constructed until a world begins loading, which unconditionally happens after every mod has finished
 * every lifecycle phase &mdash; so freezing at first actual use is the only timing that isn't at odds with Forge's
 * own load-order guarantees. Registering during your mod's own preInit/init/postInit is therefore always safe.
 * <p>
 * <b>Gate:</b> both {@link #putExtensionFactory} and {@link #addPostProcessor} refuse to do anything unless
 * {@code ConfigHolder.dev.enableExperimentalAddonExtensions} is {@code true} <i>and</i> the calling {@code modid} is
 * explicitly listed in {@code ConfigHolder.dev.experimentalAddonExtensionsAllowlist}. Both are off by default.
 * <p>
 * <b>Recommended addon-side patterns:</b>
 * <ol>
 * <li>Keep the registration calls in a class separate from your mod's main {@code @Mod} class, the same way GTST
 * itself separates recipe loading into dedicated loader classes.</li>
 * <li>To get the same field-access ergonomics as {@code config.io.itemOutput}, define your own plain data class
 * (public mutable fields, no builder, exactly GTST's own convention) and register that whole class as a single
 * {@code extensionType}. {@code getExtension(MyAddonConfig.class)} then reads and writes exactly like any of
 * GTST's own config sub-groups &mdash; {@code config.experimental.myField} itself isn't possible (Java's type
 * system won't allow adding a field to a class you don't own), but this reproduces the same feel.</li>
 * <li>Wrap registration in {@code try-catch (ExperimentalExtensionException e)}: the gate being closed or a type
 * collision with another addon are both routine, recoverable conditions, not reasons to abort your own mod's
 * initialization.</li>
 * </ol>
 */
public final class ExperimentalRecipeLogicRegistry {

    /**
     * The only {@code targetType}s for which {@linkplain Class#isAssignableFrom assignable-match} resolution is
     * attempted, in addition to the exact-match every {@code targetType} always gets. This is a fixed, hardcoded
     * set of GTST's own stable machine-family base classes &mdash; not a runtime-configurable option like the
     * modid allowlist &mdash; because it represents a judgment about which of GTST's own abstraction boundaries
     * are stable enough to be safely targeted broadly, a decision only GTST's own maintainers can make as the
     * codebase evolves.
     */
    private static final Set<Class<? extends MetaTileEntity>> ALLOWED_BROAD_TARGETS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    RecipeWorkableTieredMetaTileEntity.class,
                    RecipeWorkableSimpleMachineMetaTileEntity.class,
                    RecipeWorkableSimpleMachineMetaTileEntityResizable.class,
                    RecipeWorkableGeneratorMetaTileEntity.class,
                    RecipeWorkableMultiblockController.class,
                    RecipeWorkableSteamMultiblockController.class,
                    RecipeWorkablePrimitiveMultiblockController.class,
                    SteamMetaTileEntity.class)));

    /** Keyed by {@code extensionType}. Each value list holds entries whose {@code targetType}s never overlap. */
    private static final Map<Class<?>, List<ExtensionFactoryEntry<?>>> extensionFactories = new ConcurrentHashMap<>();

    private static final List<PostProcessorEntry> postProcessors = new ObjectArrayList<>();

    /** Set the first time any machine is actually constructed this session; see this class's own JavaDoc. */
    private static volatile boolean frozen = false;

    private ExperimentalRecipeLogicRegistry() {}

    // ================================================================================================================
    // Public API (addon-facing)
    // ================================================================================================================

    /**
     * Registers a per-instance value factory: whenever a machine whose concrete class matches {@code targetType}
     * (see this class's JavaDoc on exact-vs-assignable matching) is constructed, {@code factory} is invoked with
     * that machine, and the result becomes retrievable via {@code config.experimental.getExtension(extensionType)}
     * on that specific instance.
     *
     * @throws IllegalArgumentException                    if {@code modid} is blank or not currently loaded
     * @throws ExperimentalRegistryFrozenException          if the registry has already frozen (see class JavaDoc)
     * @throws ExperimentalExtensionsNotPermittedException  if the two-tier gate isn't fully open for {@code modid}
     * @throws ExperimentalExtensionConflictException       if {@code extensionType} already has a registration
     *                                                       whose {@code targetType} overlaps this one
     */
    public static <T> void putExtensionFactory(@NotNull String modid,
                                               @NotNull Class<? extends MetaTileEntity> targetType,
                                               @NotNull Class<T> extensionType,
                                               @NotNull Function<MetaTileEntity, T> factory) {
        validateRegistration(modid);
        synchronized (ExperimentalRecipeLogicRegistry.class) {
            List<ExtensionFactoryEntry<?>> entries = extensionFactories.computeIfAbsent(extensionType,
                    k -> new ObjectArrayList<>());
            for (ExtensionFactoryEntry<?> existing : entries) {
                if (existing.targetType().isAssignableFrom(targetType) ||
                        targetType.isAssignableFrom(existing.targetType())) {
                    throw new ExperimentalExtensionConflictException(extensionType, modid, existing.modid(),
                            existing.registrationSite());
                }
            }
            entries.add(new ExtensionFactoryEntry<>(modid, targetType, extensionType, factory, new Throwable(
                    "Original registration of extension type " + extensionType.getName() + " by modid '" +
                            modid + "', targeting " + targetType.getName())));
        }
    }

    /**
     * Registers a post-processor: after {@code RecipeLogicGraphBuilder} finishes assembling the standard graph for
     * a machine whose concrete class matches {@code targetType}, {@code processor} is invoked with the
     * {@link GTStateMachineBuilder} still open for further edits. Unlike {@link #putExtensionFactory}, multiple
     * post-processors may target overlapping (or identical) machine types; all applicable ones run, most-decisive
     * {@code priority} first (see {@link ExperimentalPriority}), tied broken by registration order.
     * <p>
     * A post-processor that throws is rolled back and permanently disabled for the remainder of this session; see
     * this class's JavaDoc section on the circuit breaker for details.
     *
     * @throws IllegalArgumentException                    if {@code modid} is blank or not currently loaded
     * @throws ExperimentalRegistryFrozenException          if the registry has already frozen (see class JavaDoc)
     * @throws ExperimentalExtensionsNotPermittedException  if the two-tier gate isn't fully open for {@code modid}
     */
    public static void addPostProcessor(@NotNull String modid, @NotNull Class<? extends MetaTileEntity> targetType,
                                        @NotNull ExperimentalPriority priority,
                                        @NotNull Consumer<GTStateMachineBuilder> processor) {
        addPostProcessor(modid, targetType, priority.getValue(), processor);
    }

    /**
     * As {@link #addPostProcessor(String, Class, ExperimentalPriority, Consumer)}, but with a raw {@code int}
     * priority for a caller that needs to run between two of {@link ExperimentalPriority}'s fixed steps.
     */
    public static void addPostProcessor(@NotNull String modid, @NotNull Class<? extends MetaTileEntity> targetType,
                                        int priority, @NotNull Consumer<GTStateMachineBuilder> processor) {
        validateRegistration(modid);
        synchronized (ExperimentalRecipeLogicRegistry.class) {
            postProcessors.add(new PostProcessorEntry(modid, targetType, priority, processor, new AtomicBoolean(false)));
        }
    }

    // ================================================================================================================
    // Internal API (RecipeLogicGraphBuilder only)
    // ================================================================================================================

    /**
     * Resolves every registered {@link #putExtensionFactory} entry applicable to {@code owner}'s concrete class,
     * invokes each matching factory, and stores the results into {@code target}. Called exactly once per machine,
     * during that machine's construction.
     */
    @ApiStatus.Internal
    public static void resolveExtensionsFor(@NotNull MetaTileEntity owner, @NotNull ExperimentalConfigExtensions target) {
        freezeIfNotAlready();
        Class<? extends MetaTileEntity> concreteClass = owner.getClass();
        for (List<ExtensionFactoryEntry<?>> entries : extensionFactories.values()) {
            for (ExtensionFactoryEntry<?> factoryEntry : entries) {
                if (matches(factoryEntry.targetType(), concreteClass)) {
                    applyFactory(factoryEntry, owner, target);
                    break; // the overlap check at registration time guarantees at most one match per extension type
                }
            }
        }
    }

    /** Captures {@code entry}'s wildcard type parameter so the {@code put} call below is fully type-safe. */
    private static <T> void applyFactory(@NotNull ExtensionFactoryEntry<T> entry, @NotNull MetaTileEntity owner,
                                         @NotNull ExperimentalConfigExtensions target) {
        target.put(entry.extensionType(), entry.factory().apply(owner));
    }

    /**
     * Applies every registered {@link #addPostProcessor} entry applicable to {@code owner}'s concrete class, in
     * priority order, to {@code builder}. Each entry runs against a fresh snapshot so a thrown exception rolls
     * back only that entry's own (possibly partial) edits, never a previous entry's already-applied ones; see this
     * class's JavaDoc for the full circuit-breaker behavior.
     *
     * @return the resulting builder (may or may not be the same instance as {@code builder}, depending on whether
     *         any post-processor applied and/or failed &mdash; always use the return value, never the argument)
     */
    @ApiStatus.Internal
    public static @NotNull GTStateMachineBuilder applyPostProcessorsFor(@NotNull MetaTileEntity owner,
                                                                        @NotNull GTStateMachineBuilder builder) {
        freezeIfNotAlready();
        Class<? extends MetaTileEntity> concreteClass = owner.getClass();
        List<PostProcessorEntry> applicable = new ArrayList<>();
        synchronized (ExperimentalRecipeLogicRegistry.class) {
            for (PostProcessorEntry entry : postProcessors) {
                if (!entry.disabled().get() && matches(entry.targetType(), concreteClass)) {
                    applicable.add(entry);
                }
            }
        }
        applicable.sort(Comparator.comparingInt(PostProcessorEntry::priority).reversed());

        GTStateMachineBuilder current = builder;
        for (PostProcessorEntry entry : applicable) {
            GTStateMachineBuilder snapshot = GTStateMachineBuilder.copy(current);
            try {
                entry.processor().accept(current);
            } catch (Throwable t) {
                current = snapshot;
                entry.disabled().set(true);
                ExperimentalExtensionFailureLog.log(entry.modid(), entry.targetType(), t);
            }
        }
        return current;
    }

    // ================================================================================================================
    // Shared internals
    // ================================================================================================================

    /**
     * @return whether {@code targetType} applies to {@code concreteClass}: always true on an exact match, and
     *         additionally true for an assignable match if {@code targetType} is one of the fixed
     *         {@link #ALLOWED_BROAD_TARGETS}.
     */
    private static boolean matches(@NotNull Class<? extends MetaTileEntity> targetType,
                                   @NotNull Class<? extends MetaTileEntity> concreteClass) {
        if (targetType.equals(concreteClass)) return true;
        return ALLOWED_BROAD_TARGETS.contains(targetType) && targetType.isAssignableFrom(concreteClass);
    }

    /** Idempotent; see this class's JavaDoc for why freezing is tied to first actual use rather than an FML event. */
    private static void freezeIfNotAlready() {
        frozen = true;
    }

    /**
     * Wipes every registration and un-freezes the registry. This registry is process-wide static state with no
     * other way to reset it between independent scenarios, so tests in this same package call this from an
     * {@code @AfterEach} to keep one test's registrations (and one test's freeze, however triggered) from leaking
     * into the next. Never called from production code.
     */
    @ApiStatus.Internal
    static void resetForTesting() {
        synchronized (ExperimentalRecipeLogicRegistry.class) {
            extensionFactories.clear();
            postProcessors.clear();
            frozen = false;
        }
    }

    private static void validateRegistration(@NotNull String modid) {
        if (modid.isEmpty()) {
            throw new IllegalArgumentException("modid must not be blank");
        }
        if (!Loader.isModLoaded(modid)) {
            throw new IllegalArgumentException("modid '" + modid + "' is not a currently loaded mod");
        }
        if (frozen) {
            throw new ExperimentalRegistryFrozenException(modid);
        }
        if (!ConfigHolder.dev.enableExperimentalAddonExtensions) {
            throw ExperimentalExtensionsNotPermittedException.masterSwitchDisabled(modid);
        }
        if (!ArrayUtils.contains(ConfigHolder.dev.experimentalAddonExtensionsAllowlist, modid)) {
            throw ExperimentalExtensionsNotPermittedException.notInAllowlist(modid);
        }
    }

    // ================================================================================================================
    // Internal data model
    // ================================================================================================================

    @Desugar
    private record ExtensionFactoryEntry<T>(@NotNull String modid, @NotNull Class<? extends MetaTileEntity> targetType,
                                            @NotNull Class<T> extensionType,
                                            @NotNull Function<MetaTileEntity, T> factory,
                                            @NotNull Throwable registrationSite) {}

    /** {@code disabled} is the one mutable field: flipped by the circuit breaker in {@link #applyPostProcessorsFor}. */
    @Desugar
    private record PostProcessorEntry(@NotNull String modid, @NotNull Class<? extends MetaTileEntity> targetType,
                                      int priority, @NotNull Consumer<GTStateMachineBuilder> processor,
                                      @NotNull AtomicBoolean disabled) {}
}
