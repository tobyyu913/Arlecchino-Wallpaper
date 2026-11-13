package com.tobyyu.arlecchinowallpaper;

public final class WakeCycleTest {
    private static void check(boolean result, String name) { if (!result) throw new AssertionError(name); }
    public static void main(String[] args) {
        WakeCycle cycle = new WakeCycle(true);
        check(!cycle.takeOpening(), "Never animate hidden wallpaper");
        cycle.setVisible(true);
        check(cycle.takeOpening(), "Initial preview opens once");
        check(!cycle.takeOpening(), "Repeated callbacks cannot restart opening");
        cycle.finish();
        cycle.setVisible(false); cycle.setVisible(true);
        check(!cycle.takeOpening(), "Returning home must hold the open frame");
        cycle.setInteractive(false);
        check(!cycle.canDraw(), "Screen off stops rendering");
        cycle.setInteractive(true);
        check(cycle.takeOpening(), "Screen on rearms opening");
        cycle.setInteractive(true);
        check(!cycle.takeOpening(), "Late duplicate wake broadcast cannot restart it");
        cycle.setVisible(false); cycle.setVisible(true);
        check(!cycle.takeOpening(), "Interrupted opening does not replay on home return");
        cycle.setInteractive(false); cycle.setVisible(false); cycle.setInteractive(true);
        check(!cycle.takeOpening(), "Wake behind another app defers animation");
        cycle.setVisible(true);
        check(cycle.takeOpening(), "Deferred wake plays when wallpaper becomes visible");
        cycle.setInteractive(false); cycle.setInteractive(true);
        check(cycle.takeOpening(), "Second screen-on cycle opens again");
        System.out.println("PASS: initial setup, screen wake, duplicate events, visibility, interruption, deferred wake, repeated wake");
    }
}
