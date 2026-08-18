package gregtech.common.mui.widget;

import gregtech.api.mui.GTGuiTextures;

import com.cleanroommc.modularui.drawable.DynamicDrawable;
import com.cleanroommc.modularui.drawable.Rectangle;
import com.cleanroommc.modularui.value.sync.StringSyncValue;
import com.cleanroommc.modularui.widget.ParentWidget;
import com.cleanroommc.modularui.widget.Widget;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.regex.Pattern;

/**
 * A row of 4 two-digit hex fields for editing an ARGB color (in A, R, G, B order), followed by a swatch
 * previewing the resulting color. {@code colorGetter}/{@code colorSetter} are the authoritative model the
 * fields read from and write to; each field only ever touches its own byte of the color.
 */
public class WidgetARGB extends ParentWidget<WidgetARGB> {

    private static final Pattern HEX_BYTE_PATTERN = Pattern.compile("[0-9a-fA-F]{0,2}");
    private static final int FIELD_WIDTH = 20;
    private static final int FIELD_GAP = 22;
    private static final int SWATCH_WIDTH = 12;
    private static final int[] CHANNEL_SHIFTS = { 24, 16, 8, 0 };

    public WidgetARGB(int height, IntSupplier colorGetter, IntConsumer colorSetter) {
        size(FIELD_GAP * CHANNEL_SHIFTS.length + SWATCH_WIDTH, height);
        for (int i = 0; i < CHANNEL_SHIFTS.length; i++) {
            child(channelField(i * FIELD_GAP, height, CHANNEL_SHIFTS[i], colorGetter, colorSetter));
        }
        child(new Widget<>()
                .pos(FIELD_GAP * CHANNEL_SHIFTS.length + 2, 0)
                .size(SWATCH_WIDTH, height)
                .background(new DynamicDrawable(() -> new Rectangle().color(colorGetter.getAsInt()))));
    }

    private static GTTextFieldWidget channelField(int x, int height, int shift, IntSupplier colorGetter,
                                                   IntConsumer colorSetter) {
        int keepMask = ~(0xFF << shift);
        return new GTTextFieldWidget()
                .pos(x, 0)
                .size(FIELD_WIDTH, height)
                .background(GTGuiTextures.DISPLAY)
                .setPattern(HEX_BYTE_PATTERN)
                .setMaxLength(2)
                .value(new StringSyncValue(
                        () -> String.format("%02X", colorGetter.getAsInt() >> shift & 0xFF),
                        data -> colorSetter.accept(
                                (data.isEmpty() ? 0 : Integer.parseInt(data, 16)) << shift |
                                        (colorGetter.getAsInt() & keepMask))));
    }
}
