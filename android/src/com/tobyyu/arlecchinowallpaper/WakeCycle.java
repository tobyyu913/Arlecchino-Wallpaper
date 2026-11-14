package com.tobyyu.arlecchinowallpaper;

/** Main-thread state machine. All timestamps are monotonic elapsed milliseconds. */
final class WakeCycle {
    enum Phase { WAITING, LOADING, PLAYING, OPEN }
    static final long WAKE_DEBOUNCE_MS = 1500;
    static final long LOAD_TIMEOUT_MS = 2500;
    static final long PLAY_TIMEOUT_MS = 4000;
    private boolean interactive, visible, surfaceReady, presented;
    private Phase phase = Phase.WAITING;
    private long lastWakeAt, deadline;

    WakeCycle(boolean screenOn, long now) { interactive = screenOn; lastWakeAt = now; }
    void setInteractive(boolean on, long now) {
        if (interactive != on) {
            reset();
            if (on) lastWakeAt = now;
        }
        interactive = on;
    }
    // SCREEN_ON and a display waking are authoritative even if SCREEN_OFF was lost.
    void wake(long now) {
        if (!interactive || now - lastWakeAt > WAKE_DEBOUNCE_MS) { reset(); lastWakeAt = now; }
        interactive = true;
    }
    private void reset() { phase = Phase.WAITING; deadline = 0; presented = false; }
    private void interrupt() {
        if (isOpening()) {
            phase = presented ? Phase.OPEN : Phase.WAITING;
            deadline = 0;
        }
    }
    void setVisible(boolean value) { visible = value; if (!value) interrupt(); }
    void setSurfaceReady(boolean value) { surfaceReady = value; if (!value) interrupt(); }
    boolean takeOpening(long now) {
        if (!canDraw() || phase != Phase.WAITING) return false;
        phase = Phase.LOADING; deadline = now + LOAD_TIMEOUT_MS; presented = false;
        return true;
    }
    boolean decoded(long now) {
        if (!canDraw() || phase != Phase.LOADING) return false;
        if (now >= deadline) { finish(); return false; }
        phase = Phase.PLAYING; deadline = now + PLAY_TIMEOUT_MS;
        return true;
    }
    void framePresented() { if (phase == Phase.PLAYING) presented = true; }
    boolean recover(long now) {
        if (!isOpening() || deadline == 0 || now < deadline) return false;
        finish(); return true;
    }
    void finish() { phase = Phase.OPEN; deadline = 0; }
    boolean isOpening() { return phase == Phase.LOADING || phase == Phase.PLAYING; }
    boolean canDraw() { return visible && interactive && surfaceReady; }
    Phase phase() { return phase; }
}
