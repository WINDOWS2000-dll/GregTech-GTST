package gregtech.api.gui;

import gregtech.api.gui.resources.AdoptableTextureArea;
import gregtech.api.gui.resources.SizedTextureArea;
import gregtech.api.gui.resources.SteamTexture;
import gregtech.api.gui.resources.TextureArea;

public class GuiTextures {

    // GREGTECH
    public static final TextureArea GREGTECH_LOGO_DARK = TextureArea
            .fullImage("textures/gui/icon/gregtech_logo_dark.png");

    // BASE TEXTURES
    public static final TextureArea BACKGROUND = AdoptableTextureArea.fullImage("textures/gui/base/background.png", 176,
            166, 3, 3);
    // todo try to remove
    public static final TextureArea BORDERED_BACKGROUND = AdoptableTextureArea
            .fullImage("textures/gui/base/bordered_background.png", 195, 136, 4, 4);

    public static final TextureArea DISPLAY = AdoptableTextureArea.fullImage("textures/gui/base/display.png", 143, 75,
            2, 2);
    public static final TextureArea FLUID_SLOT = AdoptableTextureArea.fullImage("textures/gui/base/fluid_slot.png", 18,
            18, 1, 1);
    public static final TextureArea SLOT = AdoptableTextureArea.fullImage("textures/gui/base/slot.png", 18, 18, 1, 1);
    public static final TextureArea SLOT_DARK = AdoptableTextureArea.fullImage("textures/gui/base/slot_dark.png", 18,
            18, 1, 1);
    @Deprecated // idek what this texture is
    public static final TextureArea SLOT_DARKENED = TextureArea.fullImage("textures/gui/base/darkened_slot.png");
    public static final SteamTexture SLOT_STEAM = SteamTexture.fullImage("textures/gui/base/slot_%s.png");
    public static final TextureArea TOGGLE_BUTTON_BACK = TextureArea
            .fullImage("textures/gui/widget/toggle_button_background.png");

    // FLUID & ITEM OUTPUT BUTTONS
    public static final TextureArea BUTTON_CLEAR_GRID = TextureArea
            .fullImage("textures/gui/widget/button_clear_grid.png");
    public static final SizedTextureArea VANILLA_BUTTON = SizedTextureArea
            .fullImage("textures/gui/widget/vanilla_button.png", 200, 40);
    public static final TextureArea BUTTON_POWER = TextureArea.fullImage("textures/gui/widget/button_power.png");
    // WIDGET UI RELATED
    public static final TextureArea SLIDER_BACKGROUND_VERTICAL = TextureArea
            .fullImage("textures/gui/widget/slider_background_vertical.png");
    public static final TextureArea SLIDER_ICON = TextureArea.fullImage("textures/gui/widget/slider.png");

    // SLOT OVERLAYS
    public static final TextureArea ATOMIC_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/atomic_overlay_1.png");
    public static final TextureArea ATOMIC_OVERLAY_2 = TextureArea
            .fullImage("textures/gui/overlay/atomic_overlay_2.png");
    public static final TextureArea BEAKER_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/beaker_overlay_1.png");
    public static final TextureArea BEAKER_OVERLAY_4 = TextureArea
            .fullImage("textures/gui/overlay/beaker_overlay_4.png");
    public static final TextureArea BENDER_OVERLAY = TextureArea.fullImage("textures/gui/overlay/bender_overlay.png");
    public static final TextureArea BOX_OVERLAY = TextureArea.fullImage("textures/gui/overlay/box_overlay.png");
    public static final TextureArea BOXED_OVERLAY = TextureArea.fullImage("textures/gui/overlay/boxed_overlay.png");
    public static final TextureArea BREWER_OVERLAY = TextureArea.fullImage("textures/gui/overlay/brewer_overlay.png");
    public static final TextureArea CANNER_OVERLAY = TextureArea.fullImage("textures/gui/overlay/canner_overlay.png");
    public static final TextureArea CANISTER_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/canister_overlay.png");
    public static final TextureArea CENTRIFUGE_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/centrifuge_overlay.png");
    public static final TextureArea CIRCUIT_OVERLAY = TextureArea.fullImage("textures/gui/overlay/circuit_overlay.png");
    public static final TextureArea COMPRESSOR_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/compressor_overlay.png");
    public static final TextureArea CRACKING_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/cracking_overlay_1.png");
    public static final TextureArea CRACKING_OVERLAY_2 = TextureArea
            .fullImage("textures/gui/overlay/cracking_overlay_2.png");
    public static final TextureArea CRUSHED_ORE_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/crushed_ore_overlay.png");
    public static final TextureArea CRYSTAL_OVERLAY = TextureArea.fullImage("textures/gui/overlay/crystal_overlay.png");
    public static final TextureArea CUTTER_OVERLAY = TextureArea.fullImage("textures/gui/overlay/cutter_overlay.png");
    public static final TextureArea DARK_CANISTER_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/dark_canister_overlay.png");
    public static final TextureArea DUST_OVERLAY = TextureArea.fullImage("textures/gui/overlay/dust_overlay.png");
    public static final TextureArea EXTRACTOR_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/extractor_overlay.png");
    public static final TextureArea FURNACE_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/furnace_overlay_1.png");
    public static final TextureArea FURNACE_OVERLAY_2 = TextureArea
            .fullImage("textures/gui/overlay/furnace_overlay_2.png");
    public static final TextureArea HAMMER_OVERLAY = TextureArea.fullImage("textures/gui/overlay/hammer_overlay.png");
    public static final TextureArea HEATING_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/heating_overlay_1.png");
    public static final TextureArea HEATING_OVERLAY_2 = TextureArea
            .fullImage("textures/gui/overlay/heating_overlay_2.png");
    public static final TextureArea IMPLOSION_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/implosion_overlay_1.png");
    public static final TextureArea IMPLOSION_OVERLAY_2 = TextureArea
            .fullImage("textures/gui/overlay/implosion_overlay_2.png");
    public static final TextureArea INGOT_OVERLAY = TextureArea.fullImage("textures/gui/overlay/ingot_overlay.png");
    public static final TextureArea INT_CIRCUIT_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/int_circuit_overlay.png");
    public static final TextureArea LENS_OVERLAY = TextureArea.fullImage("textures/gui/overlay/lens_overlay.png");
    public static final TextureArea LIGHTNING_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/lightning_overlay_1.png");
    public static final TextureArea LIGHTNING_OVERLAY_2 = TextureArea
            .fullImage("textures/gui/overlay/lightning_overlay_2.png");
    public static final TextureArea MOLD_OVERLAY = TextureArea.fullImage("textures/gui/overlay/mold_overlay.png");
    public static final TextureArea MOLECULAR_OVERLAY_1 = TextureArea
            .fullImage("textures/gui/overlay/molecular_overlay_1.png");
    public static final TextureArea MOLECULAR_OVERLAY_2 = TextureArea
            .fullImage("textures/gui/overlay/molecular_overlay_2.png");
    public static final TextureArea MOLECULAR_OVERLAY_3 = TextureArea
            .fullImage("textures/gui/overlay/molecular_overlay_3.png");
    public static final TextureArea MOLECULAR_OVERLAY_4 = TextureArea
            .fullImage("textures/gui/overlay/molecular_overlay_4.png");
    public static final TextureArea PIPE_OVERLAY_2 = TextureArea.fullImage("textures/gui/overlay/pipe_overlay_2.png");
    public static final TextureArea PIPE_OVERLAY_1 = TextureArea.fullImage("textures/gui/overlay/pipe_overlay_1.png");
    public static final TextureArea SAWBLADE_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/sawblade_overlay.png");
    public static final TextureArea SOLIDIFIER_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/solidifier_overlay.png");
    public static final TextureArea VIAL_OVERLAY_1 = TextureArea.fullImage("textures/gui/overlay/vial_overlay_1.png");
    public static final TextureArea VIAL_OVERLAY_2 = TextureArea.fullImage("textures/gui/overlay/vial_overlay_2.png");
    public static final TextureArea WIREMILL_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/wiremill_overlay.png");
    public static final TextureArea POSITIVE_MATTER_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/positive_matter_overlay.png");
    public static final TextureArea NEUTRAL_MATTER_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/neutral_matter_overlay.png");
    public static final TextureArea DATA_ORB_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/data_orb_overlay.png");
    public static final TextureArea SCANNER_OVERLAY = TextureArea.fullImage("textures/gui/overlay/scanner_overlay.png");
    public static final TextureArea RESEARCH_STATION_OVERLAY = TextureArea
            .fullImage("textures/gui/overlay/research_station_overlay.png");

    // PROGRESS BARS
    public static final TextureArea PROGRESS_BAR_ARC_FURNACE = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_arc_furnace.png");
    public static final TextureArea PROGRESS_BAR_ARROW = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_arrow.png");
    public static final TextureArea PROGRESS_BAR_ARROW_MULTIPLE = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_arrow_multiple.png");
    public static final TextureArea PROGRESS_BAR_BATH = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_bath.png");
    public static final TextureArea PROGRESS_BAR_BENDING = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_bending.png");
    public static final TextureArea PROGRESS_BAR_CANNER = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_canner.png");
    public static final TextureArea PROGRESS_BAR_CIRCUIT = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_circuit.png");
    public static final TextureArea PROGRESS_BAR_CIRCUIT_ASSEMBLER = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_circuit_assembler.png");
    public static final TextureArea PROGRESS_BAR_COMPRESS = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_compress.png");
    public static final TextureArea PROGRESS_BAR_CRACKING = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_cracking.png");
    public static final TextureArea PROGRESS_BAR_CRYSTALLIZATION = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_crystallization.png");
    public static final TextureArea PROGRESS_BAR_EXTRACT = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_extract.png");
    public static final TextureArea PROGRESS_BAR_EXTRUDER = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_extruder.png");
    public static final TextureArea PROGRESS_BAR_FUSION = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_fusion.png");
    public static final TextureArea PROGRESS_BAR_GAS_COLLECTOR = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_gas_collector.png");
    public static final TextureArea PROGRESS_BAR_HAMMER = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_hammer.png");
    public static final TextureArea PROGRESS_BAR_HAMMER_BASE = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_hammer_base.png");
    public static final TextureArea PROGRESS_BAR_LATHE = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_lathe.png");
    public static final TextureArea PROGRESS_BAR_LATHE_BASE = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_lathe_base.png");
    public static final TextureArea PROGRESS_BAR_MACERATE = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_macerate.png");
    public static final TextureArea PROGRESS_BAR_MAGNET = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_magnet.png");
    public static final TextureArea PROGRESS_BAR_MASS_FAB = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_mass_fab.png");
    public static final TextureArea PROGRESS_BAR_MIXER = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_mixer.png");
    public static final TextureArea PROGRESS_BAR_REPLICATOR = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_replicator.png");
    public static final TextureArea PROGRESS_BAR_SIFT = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_sift.png");
    public static final TextureArea PROGRESS_BAR_SLICE = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_slice.png");
    public static final SteamTexture PROGRESS_BAR_SOLAR_STEAM = SteamTexture
            .fullImage("textures/gui/progress_bar/progress_bar_solar_%s.png");
    public static final TextureArea PROGRESS_BAR_UNPACKER = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_unpacker.png");
    public static final TextureArea PROGRESS_BAR_WIREMILL = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_wiremill.png");
    public static final TextureArea PROGRESS_BAR_MULTI_ENERGY_YELLOW = TextureArea
            .fullImage("textures/gui/progress_bar/progress_bar_multi_energy_yellow.png");

    // JEI
    public static final TextureArea INFO_ICON = TextureArea.fullImage("textures/gui/widget/information.png");
    public static final TextureArea MULTIBLOCK_CATEGORY = TextureArea
            .fullImage("textures/gui/icon/multiblock_category.png");
    public static final TextureArea ARC_FURNACE_RECYLCING_CATEGORY = TextureArea
            .fullImage("textures/gui/icon/arc_furnace_recycling.png");
    public static final TextureArea MACERATOR_RECYLCING_CATEGORY = TextureArea
            .fullImage("textures/gui/icon/macerator_recycling.png");
    public static final TextureArea EXTRACTOR_RECYLCING_CATEGORY = TextureArea
            .fullImage("textures/gui/icon/extractor_recycling.png");

    // Ore Filter
    public static final TextureArea ORE_FILTER_SUCCESS = TextureArea
            .fullImage("textures/gui/widget/ore_filter/success.png");
    public static final TextureArea ORE_FILTER_ERROR = TextureArea
            .fullImage("textures/gui/widget/ore_filter/error.png");
    public static final TextureArea ORE_FILTER_WARN = TextureArea.fullImage("textures/gui/widget/ore_filter/warn.png");
    public static final TextureArea ORE_FILTER_WAITING = TextureArea
            .fullImage("textures/gui/widget/ore_filter/waiting.png");

    // Texture Areas
    public static final TextureArea COVER_INTERFACE_MACHINE_ON_PROXY = TextureArea
            .fullImage("textures/blocks/cover/cover_interface_machine_on_proxy.png");
    public static final TextureArea COVER_INTERFACE_MACHINE_OFF_PROXY = TextureArea
            .fullImage("textures/blocks/cover/cover_interface_machine_off_proxy.png");

    // Lamp item overlay
    public static final TextureArea LAMP_NO_BLOOM = TextureArea
            .fullImage("textures/gui/item_overlay/lamp_no_bloom.png");
    public static final TextureArea LAMP_NO_LIGHT = TextureArea
            .fullImage("textures/gui/item_overlay/lamp_no_light.png");

}
