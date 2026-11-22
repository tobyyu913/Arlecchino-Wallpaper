package com.tobyyu.arlecchinowallpaper;
import android.app.*;
import android.content.*;
import android.os.*;
import android.widget.*;
import android.view.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NotificationTests extends Instrumentation {
  HomeActivity activity; TextView body; LinearLayout actions;
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
      runOnMainSync(()->{
        body=new TextView(activity);actions=new LinearLayout(activity);actions.setOrientation(LinearLayout.VERTICAL);
        LinearLayout fixture=new LinearLayout(activity);fixture.setOrientation(LinearLayout.VERTICAL);fixture.addView(body);fixture.addView(actions);
        activity.addContentView(fixture,new android.view.ViewGroup.LayoutParams(-1,-2));
        activity.bindNotification(body,actions,"test-app","Test app",preview,"");
        check(body.getMaxLines()==3 && actions.getVisibility()==View.GONE,"Preview must start collapsed");
        body.performClick();
        check(full.contentEquals(body.getText()) && body.getMaxLines()==Integer.MAX_VALUE,"Full notification did not expand inline");
        check(actions.getVisibility()==View.VISIBLE,"Inline actions missing");
      });waitForIdleSync();
      check(opens.get()==0,"Expanding notification sent its intent");
      runOnMainSync(()->{
        activity.bindNotification(body,actions,"test-app","Test app",preview,"");
        check(body.getMaxLines()==Integer.MAX_VALUE,"Rebinding lost expansion");
        ((LinearLayout)actions.getChildAt(1)).getChildAt(1).performClick();
        check(body.getMaxLines()==3&&actions.getVisibility()==View.GONE,"Collapse failed");
      });waitForIdleSync();check(opens.get()==0,"Collapse sent intent");
      runOnMainSync(()->{
        body.performClick();
        ((LinearLayout)actions.getChildAt(0)).getChildAt(0).performClick();
      });
      long deadline=SystemClock.uptimeMillis()+2000;while(opens.get()==0&&SystemClock.uptimeMillis()<deadline)SystemClock.sleep(20);
      check(opens.get()==1,"Explicit open must send intent exactly once");open.cancel();
      runOnMainSync(()->{
        activity.bindNotification(body,actions,"other-app","Other app",null,"Recently used");
        check(!body.isClickable()&&actions.getVisibility()==View.GONE&&body.getMaxLines()==3,"Recycled row leaked notification state");
        HomeNotifications.Preview replacement=new HomeNotifications.Preview("replacement","New preview","New full text",null,1,null);
        activity.bindNotification(body,actions,"test-app","Test app",replacement,"");
        check(body.getMaxLines()==3,"Different notification inherited expansion");
        body.performClick();check("New full text".contentEquals(body.getText()),"Non-actionable notification did not expand");
        check(actions.getChildAt(0).getVisibility()==View.GONE,"Missing content intent must not show Open");
        activity.bindNotification(body,actions,"test-app","Test app",null,"");
        check(body.getVisibility()==View.GONE&&actions.getVisibility()==View.GONE,"Removed notification left expanded content");
      });
      Notification inbox=new Notification.Builder(context,"test").setStyle(new Notification.InboxStyle().addLine("First line").addLine("Second line")).build();
      check(HomeNotifications.expandedText(inbox).contains("First line\nSecond line"),"Inbox lines missing");
      Notification messages=new Notification.Builder(context,"test").setStyle(new Notification.MessagingStyle(new Person.Builder().setName("Me").build()).addMessage("Message body",1,new Person.Builder().setName("Sender").build())).build();
      check(HomeNotifications.expandedText(messages).contains("Sender: Message body"),"Conversation body missing");
      result.putString("stream","PASS: inline expansion/collapse and rebind; recycled/replaced/removed notification cleanup; full text formats; only explicit Open sends intent\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream","FAIL: "+t);finish(Activity.RESULT_CANCELED,result);}
    finally{context.unregisterReceiver(receiver);}
  }
}
