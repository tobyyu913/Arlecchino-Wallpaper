package com.tobyyu.arlecchinowallpaper;
public final class NavigationZonesTest {
  static void check(boolean result){if(!result)throw new AssertionError();}
  public static void main(String[] args){
    int width=1080,height=2400,left=72,right=72,bottom=96;
    check(NavigationZones.systemStart(2,1000,width,height,left,right,bottom));
    check(NavigationZones.systemStart(1078,1000,width,height,left,right,bottom));
    check(NavigationZones.systemStart(540,2399,width,height,left,right,bottom));
    check(!NavigationZones.alphabetStart(2,1000,width,height,left,right,bottom,60,144));
    check(!NavigationZones.alphabetStart(1078,1000,width,height,left,right,bottom,60,144));
    check(NavigationZones.alphabetStart(80,1000,width,height,left,right,bottom,60,144));
    check(NavigationZones.alphabetStart(990,1000,width,height,left,right,bottom,60,144));
    check(!NavigationZones.alphabetStart(990,2399,width,height,left,right,bottom,60,144));
    check(NavigationZones.alphabetStart(1078,1000,width,height,0,0,bottom,60,144));
    check(!NavigationZones.systemStart(540,1000,width,height,left,right,bottom));
    check(NavigationZones.systemStart(2000,1079,2400,1080,0,90,96));
    check(!NavigationZones.alphabetStart(2399,500,2400,1080,0,90,96,60,144));
    System.out.println("PASS: Back edges and bottom Home/Recents stay reserved; inward alphabet bands work in gesture and button modes, portrait and landscape");
  }
}
