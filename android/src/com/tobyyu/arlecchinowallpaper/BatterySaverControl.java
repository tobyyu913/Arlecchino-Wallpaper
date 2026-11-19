package com.tobyyu.arlecchinowallpaper;

import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.widget.Toast;
import java.util.concurrent.*;
import rikka.shizuku.Shizuku;

/** Optional shell-backed battery toggle, bounded work off the UI thread. */
final class BatterySaverControl {
  private final Activity activity;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private final ExecutorService worker=Executors.newSingleThreadExecutor();
  private final Runnable changed;
  private IBinder service;
  private boolean binding,closed,busy,permissionPending;
  private Boolean requested;
  private final Shizuku.UserServiceArgs args;
  private final ServiceConnection connection=new ServiceConnection(){
    @Override public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;service=binder;binding=false;execute();}
    @Override public void onServiceDisconnected(ComponentName name){service=null;binding=false;fail("Shizuku stopped. Start it again to use Battery Saver here.");}
  };
  private final Shizuku.OnRequestPermissionResultListener permission=(request,result)->{
    if(request!=717)return;permissionPending=false;
    if(closed)return;
    if(result==PackageManager.PERMISSION_GRANTED)bind();else fail("Battery Saver needs the Shizuku access you choose to allow.");
  };
  BatterySaverControl(Activity activity,Runnable changed){this.activity=activity;this.changed=changed;
    args=new Shizuku.UserServiceArgs(new ComponentName(activity,BatterySaverService.class)).daemon(false).processNameSuffix("battery_saver").version(1);
    Shizuku.addRequestPermissionResultListener(permission);
  }
  boolean awaitingPermission(){return permissionPending;}
  boolean busy(){return busy;}
  boolean available(){try{return Shizuku.pingBinder()&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(RuntimeException ignored){return false;}}
  void set(boolean enabled){if(busy||closed)return;
    PowerManager power=activity.getSystemService(PowerManager.class);
    if(enabled){Intent battery=activity.registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(battery!=null&&battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0){Toast.makeText(activity,"Unplug the charger to enable Android Battery Saver.",Toast.LENGTH_LONG).show();return;}}
    requested=enabled;busy=true;changed.run();
    try {
      if(!Shizuku.pingBinder()){
        busy=false;requested=null;changed.run();
        new AlertDialog.Builder(activity).setTitle("Battery Saver control").setMessage("Start Shizuku, then allow Arlecchino Home. After that this button switches Battery Saver on and off here, without root.")
          .setPositiveButton("Open Shizuku",(d,w)->{Intent launch=activity.getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(launch!=null)activity.startActivity(launch);else Toast.makeText(activity,"Install Shizuku from shizuku.rikka.app",Toast.LENGTH_LONG).show();}).setNegativeButton("Cancel",null).show();return;
      }
      if(Shizuku.checkSelfPermission()!=PackageManager.PERMISSION_GRANTED){permissionPending=true;Shizuku.requestPermission(717);return;}
      bind();
    }catch(RuntimeException failure){fail("Shizuku is unavailable. Start it again.");}
  }
  private void bind(){if(closed)return;if(service!=null&&service.isBinderAlive()){execute();return;}if(binding)return;binding=true;try{Shizuku.bindUserService(args,connection);ui.postDelayed(()->{if(binding)fail("Shizuku could not connect to Battery Saver.");},5000);}catch(RuntimeException failure){binding=false;fail("Shizuku could not start Battery Saver control.");}}
  private void execute(){if(closed||requested==null)return;boolean target=requested;requested=null;IBinder binder=service;
    worker.execute(()->{String error="";Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{data.writeInterfaceToken(BatterySaverService.DESCRIPTOR);data.writeInt(target?1:0);if(!binder.transact(BatterySaverService.SET,data,reply,0))throw new RemoteException("Unsupported service");reply.readException();error=reply.readString();}catch(Exception failure){error="Shizuku connection failed";}finally{reply.recycle();data.recycle();}final String message=error;ui.post(()->{if(closed)return;if(message!=null&&!message.isEmpty()){fail(message);return;}confirm(target,0);});});
  }
  private void confirm(boolean target,int attempt){if(closed)return;PowerManager power=activity.getSystemService(PowerManager.class);if(power!=null&&power.isPowerSaveMode()==target){busy=false;changed.run();return;}if(attempt>=8){fail("Android did not change Battery Saver. Check charging or device policy.");return;}ui.postDelayed(()->confirm(target,attempt+1),250);}
  private void fail(String message){if(closed)return;busy=false;binding=false;requested=null;changed.run();Toast.makeText(activity,message,Toast.LENGTH_LONG).show();}
  void close(){closed=true;Shizuku.removeRequestPermissionResultListener(permission);ui.removeCallbacksAndMessages(null);try{Shizuku.unbindUserService(args,connection,true);}catch(RuntimeException ignored){}worker.shutdownNow();service=null;}
}
