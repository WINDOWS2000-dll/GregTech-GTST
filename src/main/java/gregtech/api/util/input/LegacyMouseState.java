package gregtech.api.util.input;

import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

public final class LegacyMouseState {

    private static final boolean[] BUTTONS_DOWN = new boolean[8];
    private static int lastDWheel;

    private LegacyMouseState() {}

    public static boolean isButtonDown(int button) {
        return button >= 0 && button < BUTTONS_DOWN.length && BUTTONS_DOWN[button];
    }

    public static int getEventDWheel() {
        return lastDWheel;
    }

    @SubscribeEvent
    public static void onMouseEvent(MouseEvent event) {
        if (event.getButton() >= 0 && event.getButton() < BUTTONS_DOWN.length) {
            BUTTONS_DOWN[event.getButton()] = event.isButtonstate();
        }
        if (event.getDwheel() != 0) {
            lastDWheel = event.getDwheel();
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            lastDWheel = 0;
        }
    }
}
