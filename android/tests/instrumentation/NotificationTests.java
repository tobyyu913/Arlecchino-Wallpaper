package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NotificationTests extends Instrumentation {
  HomeActivity activity; AlertDialog panel;
  void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
  @Override public void onCreate(Bundle args){super.onCreate(args);start();}
  @Override public void onStart(){Bundle result=new Bundle();Context context=getTargetContext();AtomicInteger opens=new AtomicInteger();
    BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){opens.incrementAndGet();}};
    context.registerReceiver(receiver,new IntentFilter(context.getPackageName()+".TEST_OPEN"),Context.RECEIVER_NOT_EXPORTED);
    try{
      activity=(HomeActivity)startActivitySync(new Intent(context,HomeActivity.class).setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      String longText=String.join("",java.util.Collections.nCopies(80,"Full message. "));
      Notification n=new Notification.Builder(context,"test").setContentTitle("Test sender").setContentText("Short preview").setStyle(new Notification.BigTextStyle().bigText(longText)).build();
      String full=HomeNotifications.expandedText(n);
      check(full.contains(n.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString().trim()) && full.length()>240,"Expanded content supplied by Android was truncated");
      PendingIntent open=PendingIntent.getBroadcast(context,812,new Intent(context.getPackageName()+".TEST_OPEN").setPackage(context.getPackageName()),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
      HomeNotifications.Preview preview=new HomeNotifications.Preview("test","Short preview",full,open,0,null);
      runOnMainSync(()->panel=activity.showNotification("Test app",preview));waitForIdleSync();
      check(opens.get()==0,"Reading notification sent its intent");
      runOnMainSync(()->{check(panel.isShowing(),"Reader missing");panel.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();});waitForIdleSync();
      check(opens.get()==0,"Closing reader sent intent");
      runOnMainSync(()->{panel=activity.showNotification("Test app",preview);panel.getButton(AlertDialog.BUTTON_POSITIVE).performClick();});
      long deadline=SystemClock.uptimeMillis()+2000;while(opens.get()==0&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);
      check(opens.get()==1,"Explicit open must send intent exactly once");open.cancel();
      Notification inbox=new Notification.Builder(context,"test").setStyle(new Notification.InboxStyle().addLine("First line").addLine("Second line")).build();
      check(HomeNotifications.expandedText(inbox).contains("First line\nSecond line"),"Inbox lines missing");
      Notification messages=new Notification.Builder(context,"test").setStyle(new Notification.MessagingStyle(new Person.Builder().setName("Me").build()).addMessage("Message body",1,new Person.Builder().setName("Sender").build())).build();
      check(HomeNotifications.expandedText(messages).contains("Sender: Message body"),"Conversation body missing");
      result.putString("stream","PASS: full BigText/Inbox/Messaging content; reading and closing never send notification intent; explicit Open sends exactly once\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream","FAIL: "+t);finish(Activity.RESULT_CANCELED,result);}
    finally{context.unregisterReceiver(receiver);runOnMainSync(()->{if(panel!=null)panel.dismiss();});}
  }
}
