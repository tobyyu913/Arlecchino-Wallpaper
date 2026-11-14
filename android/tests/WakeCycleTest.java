package com.tobyyu.arlecchinowallpaper;

public final class WakeCycleTest {
    private static void check(boolean result, String name) { if (!result) throw new AssertionError(name); }
    private static WakeCycle visible(long now) {
        WakeCycle c = new WakeCycle(true, now); c.setVisible(true); c.setSurfaceReady(true); return c;
    }
    private static void play(WakeCycle c, long now) {
        check(c.takeOpening(now), "Opening should load");
        check(c.decoded(now + 10), "Decode accepted"); c.framePresented();
    }
    public static void main(String[] args) {
        WakeCycle c = visible(0);
        play(c, 0); c.finish();
        c.setVisible(false); c.setVisible(true);
        check(!c.takeOpening(100), "Returning home must not replay");
        c.wake(500);
        check(!c.takeOpening(500), "Duplicate wake source must not replay");
        // Regression: SCREEN_OFF was lost, so interactive was never set false.
        c.wake(5000);
        play(c, 5000);
        c.finish();
        check(c.phase() == WakeCycle.Phase.OPEN, "Missed-off wake completes open");

        c = visible(0);
        check(c.takeOpening(0), "Begin async decode");
        c.setVisible(false); c.setVisible(true);
        check(c.takeOpening(100), "Canceled decode must not consume the wake");
        check(c.decoded(110), "Retry decode succeeds");
        // A decoded drawable isn't proof that a frame reached a surface.
        c.setSurfaceReady(false); c.setSurfaceReady(true);
        check(c.takeOpening(200), "Surface lost before first frame must retry");
        check(c.decoded(210), "Surface retry loads"); c.framePresented();
        c.setSurfaceReady(false); c.setSurfaceReady(true);
        check(!c.takeOpening(300) && c.phase() == WakeCycle.Phase.OPEN,
            "Surface loss after presentation holds open without replay");

        c = visible(0);
        check(c.takeOpening(0), "Load for timeout test");
        check(!c.recover(2499), "Allow time for decoding");
        check(c.recover(2500) && c.phase() == WakeCycle.Phase.OPEN,
            "Stalled decoder must show static open frame");
        check(!c.decoded(2600), "Late decode cannot resurrect timed-out animation");
        c = visible(0); c.takeOpening(0);
        check(!c.decoded(3000) && c.phase() == WakeCycle.Phase.OPEN,
            "Late decode rejected even before watchdog gets CPU time");

        c = visible(0); play(c, 0);
        check(!c.recover(4009), "Animation deadline starts after decoding");
        check(c.recover(4010) && c.phase() == WakeCycle.Phase.OPEN,
            "Missing completion callback cannot leave animation stuck");
        check(!c.recover(5000), "Recovery is idempotent");

        c = visible(0); play(c, 0);
        c.setInteractive(false, 100); c.setInteractive(true, 110);
        play(c, 110);
        c.wake(120);
        check(!c.takeOpening(120), "Late broadcast cannot restart visibility-triggered wake");
        c.setInteractive(false, 130); c.setInteractive(true, 140);
        check(c.takeOpening(140), "Real rapid off/on rearms despite debounce");

        c = visible(0); play(c, 0); c.finish(); c.setVisible(false);
        c.setInteractive(false, 5000); c.wake(5100);
        check(!c.takeOpening(5200), "Wake under another app must defer");
        c.setVisible(true); play(c, 5300);
        c.setVisible(false); c.setVisible(true);
        check(!c.takeOpening(5400), "Interrupted visible playback holds open on return");
        c.setInteractive(false, 5500);
        check(!c.canDraw(), "No rendering while screen is off");

        c = new WakeCycle(false, 0);
        c.setVisible(true); c.setSurfaceReady(true);
        check(!c.takeOpening(0), "No playback during engine creation with screen off");
        c.wake(100); play(c, 100);
        for (int i = 0; i < 100; i++) {
            c.finish(); c.setVisible(false); c.setVisible(true);
            check(!c.takeOpening(200 + i), "Repeated home/visibility events cannot rearm");
        }
        System.out.println("PASS: missed screen-off, duplicate/rapid wake, decode cancellation, surface replacement, late decode, decoder/playback timeout, deferred visibility, 100 home returns");
    }
}
