package in.sursandconnect.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent check = new Intent(context, UpdateCheckReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(
            context, 4102, check,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager alarm = (AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
        long every = 60L * 60L * 1000L;
        alarm.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + 60_000L,
            every,
            pi
        );
    }
}
