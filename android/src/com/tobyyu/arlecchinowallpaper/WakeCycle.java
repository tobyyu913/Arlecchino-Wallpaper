package com.tobyyu.arlecchinowallpaper;

/** One opening per interactive screen cycle; launcher visibility never rearms it. */
final class WakeCycle {
    private boolean interactive;
    private boolean visible;
    private boolean pending = true;
    private boolean opening;

    WakeCycle(boolean screenOn) { interactive = screenOn; }
    void setInteractive(boolean on) {
        if (interactive && !on) { pending = true; opening = false; }
        interactive = on;
    }
    void setVisible(boolean value) {
        visible = value;
        if (!value) opening = false;
    }
    boolean takeOpening() {
        if (!visible || !interactive || !pending) return false;
        pending = false;
        opening = true;
        return true;
    }
    void finish() { opening = false; }
    boolean isOpening() { return opening; }
    boolean canDraw() { return visible && interactive; }
}
