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
      java.lang.Process process=new ProcessBuilder("/system/bin/cmd","power","set-mode",enabled?"1":"0").redirectErrorStream(true).start();
      if(!process.waitFor(4,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Battery saver timed out");}
      if(process.exitValue()!=0){byte[] buffer=new byte[1024];int count=process.getInputStream().read(buffer);error=count>0?new String(buffer,0,count,"UTF-8"):"Android rejected the change";}
      process.getInputStream().close();process.getOutputStream().close();process.getErrorStream().close();
    }catch(Exception failure){error=failure.getMessage()==null?"Battery saver unavailable":failure.getMessage();}
    reply.writeNoException();reply.writeString(error);return true;
  }
}
