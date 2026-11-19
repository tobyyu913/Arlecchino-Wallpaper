package com.tobyyu.arlecchinowallpaper;
/** Test-only provider protected from shell with a signature permission, like HyperOS. */
public final class OemPowerProvider extends android.content.ContentProvider {
  public boolean onCreate(){return true;}
  public android.os.Bundle call(String method,String arg,android.os.Bundle extras){throw new AssertionError("Protected OEM provider was reached");}
  public android.database.Cursor query(android.net.Uri u,String[]p,String s,String[]a,String o){return null;}
  public String getType(android.net.Uri u){return null;}
  public android.net.Uri insert(android.net.Uri u,android.content.ContentValues v){return null;}
  public int delete(android.net.Uri u,String s,String[]a){return 0;}
  public int update(android.net.Uri u,android.content.ContentValues v,String s,String[]a){return 0;}
}
