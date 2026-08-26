package in.sursandconnect.app;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.app.Notification;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class UpdateCheckReceiver extends BroadcastReceiver {
    private static final String API = "https://script.google.com/macros/s/AKfycbzrDLNOj56LEjyLuf4PJiBsH5s36RgRi6y55NjjVJtxvRjFPUMdcXLsOK4ojNUssdNX/exec?action=all";

    @Override
    public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(() -> {
            try { check(context); } catch (Exception ignored) {} finally { pending.finish(); }
        }).start();
    }

    private void check(Context context) throws Exception {
        HttpURLConnection c = (HttpURLConnection)new URL(API).openConnection();
        c.setConnectTimeout(12000); c.setReadTimeout(12000); c.setRequestMethod("GET");
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream()));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close(); c.disconnect();

        JSONObject root = new JSONObject(sb.toString());
        if (!root.optBoolean("success", false)) return;
        JSONObject data = root.optJSONObject("data");
        if (data == null) return;

        JSONObject latest = latestOf(data.optJSONArray("events"), data.optJSONArray("announcements"), data.optJSONArray("notifications"));
        if (latest == null) return;

        String id = first(latest, "ID", "Id", "id", "Title", "Event Title") + "|" + first(latest, "Created At", "Date", "Event Date", "Timestamp");
        if (id.equals("|")) return;

        SharedPreferences sp = context.getSharedPreferences("sursand_native", Context.MODE_PRIVATE);
        String seen = sp.getString("latest_event_announcement", "");
        if (id.equals(seen)) return;
        sp.edit().putString("latest_event_announcement", id).apply();

        String title = first(latest, "Title", "Event Title", "Notification Title", "Name");
        String body = first(latest, "Description", "Details", "Message", "Summary", "Content");
        if (title.isEmpty() && body.isEmpty()) return;
        if (title.isEmpty()) title = "Sursand Connect Update";
        if (body.length() > 180) body = body.substring(0, 177) + "...";

        Intent open = new Intent(context, MainActivity.class);
        open.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(context, 4200, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, "sursand_updates")
                : new Notification.Builder(context);
        b.setSmallIcon(R.mipmap.ic_launcher)
         .setContentTitle(title)
         .setContentText(body)
         .setStyle(new Notification.BigTextStyle().bigText(body))
         .setAutoCancel(true)
         .setContentIntent(pi);
        ((NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE)).notify(4201, b.build());
    }

    private JSONObject latestOf(JSONArray... arrays) {
        JSONObject best = null; long bestTime = Long.MIN_VALUE;
        for (JSONArray arr : arrays) {
            if (arr == null) continue;
            for (int i=0;i<arr.length();i++) {
                JSONObject o = arr.optJSONObject(i); if (o == null || expired(o) || notStarted(o)) continue;
                long t = parseTime(first(o,"Created At","Date","Event Date","Timestamp","Start Date"));
                if (best == null || t >= bestTime) { best = o; bestTime = t; }
            }
        }
        return best;
    }

    private boolean expired(JSONObject o) {
        String end = first(o, "End Date");
        if (end.isEmpty()) return false;
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Date d = f.parse(end.substring(0, Math.min(10, end.length())));
            return d != null && d.getTime() + 86399999L < System.currentTimeMillis();
        } catch (Exception e) { return false; }
    }

    private boolean notStarted(JSONObject o) {
        String start = first(o, "Start Date");
        if (start.isEmpty()) return false;
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
            java.util.Date d = f.parse(start.substring(0, Math.min(10, start.length())));
            return d != null && d.getTime() > System.currentTimeMillis();
        } catch (Exception e) { return false; }
    }

    private String first(JSONObject o, String... keys) {
        for (String k: keys) { String v = o.optString(k, "").trim(); if (!v.isEmpty()) return v; }
        return "";
    }

    private long parseTime(String s) {
        if (s == null || s.isEmpty()) return 0;
        // IDs/order are still a fallback when date parsing is unavailable.
        return s.hashCode();
    }
}
