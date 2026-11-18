package com.tobyyu.arlecchinowallpaper;
public final class NetworkLabelTest {
  static void check(String actual,String expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
  public static void main(String[] args){check(NetworkLabel.mobile(20,0),"5G");check(NetworkLabel.mobile(13,3),"5G");check(NetworkLabel.mobile(13,5),"5G");check(NetworkLabel.mobile(13,0),"4G");check(NetworkLabel.mobile(19,0),"4G");check(NetworkLabel.mobile(10,0),"3G");check(NetworkLabel.mobile(2,0),"2G");check(NetworkLabel.mobile(0,0),"DATA");System.out.println("PASS: standalone/NSA/advanced 5G, LTE 4G, older generations and unknown fallback");}
}
