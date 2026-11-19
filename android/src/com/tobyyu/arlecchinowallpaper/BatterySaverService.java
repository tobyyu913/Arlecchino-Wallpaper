package com.tobyyu.arlecchinowallpaper;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.concurrent.TimeUnit;

/** Shizuku UserService: one fixed battery-saver operation, never arbitrary shell input. */
public final class BatterySaverService extends Binder {
  static final String DESCRIPTOR="com.tobyyu.arlecchinowallpaper.BatterySaver";
  static final int SET=1, DESTROY=16777115;
  private final int owner;
  public BatterySaverService(Context context) throws Exception {
    owner=context.getPackageManager().getApplicationInfo("com.tobyyu.arlecchinowallpaper",0).uid;
    attachInterface(null,DESCRIPTOR);
  }
  @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags) throws RemoteException {
    if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
    if(code==DESTROY){int uid=Binder.getCallingUid();if(uid!=owner&&uid!=2000&&uid!=0)throw new SecurityException("Not owner");System.exit(0);return true;}
    if(code!=SET)return super.onTransact(code,data,reply,flags);
    data.enforceInterface(DESCRIPTOR);
    if(Binder.getCallingUid()!=owner)throw new SecurityException("Not owner");
    boolean enabled=data.readInt()!=0;
    String error="";
    try {
      String nativeState=command("/system/bin/settings","get","system",BatterySaverState.XIAOMI_KEY).trim();
      if("0".equals(nativeState)||"1".equals(nativeState)) {
        if((enabled?"1":"0").equals(nativeState))error="";
        else {
          String output=command("/system/bin/content","call","--uri","content://com.miui.powercenter.powersaver","--method","changePowerMode","--extra","POWER_SAVE_MODE_OPEN:b:"+enabled);
          if(output.contains("SecurityException")||output.contains("Permission Denial"))error="OEM_PERMISSION";
          else if(output.contains("Error")||output.contains("Exception"))error="OEM_UNAVAILABLE";
        }
      } else command("/system/bin/cmd","power","set-mode",enabled?"1":"0");
    }catch(Exception failure){error=failure.getMessage()==null?"Battery saver unavailable":failure.getMessage();}
    reply.writeNoException();reply.writeString(error);return true;
  }
  private static String command(String... arguments) throws Exception {
    java.lang.Process process=new ProcessBuilder(arguments).redirectErrorStream(true).start();
    if(!process.waitFor(4,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Battery Saver timed out");}
    ByteArrayOutputStream output=new ByteArrayOutputStream();byte[] buffer=new byte[1024];int count;
    while((count=process.getInputStream().read(buffer))!=-1){if(output.size()<8192)output.write(buffer,0,Math.min(count,8192-output.size()));}
    process.getInputStream().close();process.getOutputStream().close();process.getErrorStream().close();
    String result=output.toString("UTF-8");
    if(process.exitValue()!=0&&!result.contains("Permission Denial"))throw new IOException("Android rejected Battery Saver");
    return result;
  }
}
