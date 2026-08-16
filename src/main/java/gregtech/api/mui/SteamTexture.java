package gregtech.api.mui;

import gregtech.api.GTValues;

import com.cleanroommc.modularui.drawable.UITexture;

/**
 * A simple wrapper around two {@link UITexture} Objects used to
 * ease in choosing between Bronze Steam and Steel Steam textures.
 */
public class SteamTexture {

    private static final String BRONZE = "bronze";
    private static final String STEEL = "steel";

    private final UITexture bronzeTexture;
    private final UITexture steelTexture;

    private SteamTexture(UITexture bronzeTexture, UITexture steelTexture) {
        this.bronzeTexture = bronzeTexture;
        this.steelTexture = steelTexture;
    }

    public static SteamTexture fullImage(String path) {
        return new SteamTexture(
                UITexture.fullImage(GTValues.MODID, String.format(path, BRONZE)),
                UITexture.fullImage(GTValues.MODID, String.format(path, STEEL)));
    }

    /**
     * Wraps two pre-built {@link UITexture}s, for cases which need more than a plain full-image texture
     * (e.g. adaptable textures or textures registered under a theme id).
     */
    public static SteamTexture of(UITexture bronzeTexture, UITexture steelTexture) {
        return new SteamTexture(bronzeTexture, steelTexture);
    }

    public UITexture get(boolean isHighPressure) {
        return isHighPressure ? steelTexture : bronzeTexture;
    }
}
