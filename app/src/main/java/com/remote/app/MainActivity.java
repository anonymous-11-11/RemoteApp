package com.remote.app;

import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Vibrator;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    // ⚠️ তোমার ল্যাপটপের আইপি (যেমন: http://192.168.0.105:8080) অথবা Render-এর লিংক এখানে দাও
    public static final String SERVER_URL = "http://192.168.10.107:8080"; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // অ্যাপের সিম্পল ডার্ক ইন্টারফেস (XML ফাইল ছাড়াই কোড দিয়ে তৈরি)
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 100, 50, 50);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFF0F172A); // Slate 900 ডার্ক কালার

        TextView title = new TextView(this);
        title.setText("📱 Remote Device Client");
        title.setTextSize(22);
        title.setTextColor(0xFF38BDF8); // Light Blue
        title.setPadding(0, 0, 0, 40);
        layout.addView(title);

        Button btnGrant = new Button(this);
        btnGrant.setText("GRANT & CONNECT");
        btnGrant.setBackgroundColor(0xFF38BDF8);
        btnGrant.setTextColor(0xFF0F172A);
        btnGrant.setPadding(20, 20, 20, 20);

        btnGrant.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, RemoteService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "Service Activated!", Toast.LENGTH_SHORT).show();
            btnGrant.setEnabled(false);
            btnGrant.setText("✅ CONNECTED & RUNNING");
            btnGrant.setBackgroundColor(0xFF4ADE80); // Green color
        });

        layout.addView(btnGrant);
        setContentView(layout);

        // ড্যাশবোর্ড থেকে মেসেজ আসলে স্ক্রিনে পপ-আপ দেখানোর জন্য
        if (getIntent().hasExtra("alert_message")) {
            showAlertMessage(getIntent().getStringExtra("alert_message"));
        }
    }

    private void showAlertMessage(String msg) {
        new AlertDialog.Builder(this)
                .setTitle("💬 Message from Admin")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show();
    }

    // ==========================================
    // ২. ব্যাকগ্রাউন্ড সার্ভিস (একই ফাইলের ভেতরে)
    // ==========================================
    public static class RemoteService extends Service {
        private boolean isRunning = false;
        private Ringtone ringtone = null;

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForegroundNotification();
            if (!isRunning) {
                isRunning = true;
                startPollingServer();
            }
            return START_STICKY;
        }

        private void startForegroundNotification() {
            String channelId = "remote_channel";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        channelId, "Remote Service", NotificationManager.IMPORTANCE_LOW);
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) manager.createNotificationChannel(channel);
            }

            Notification notification = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                notification = new Notification.Builder(this, channelId)
                        .setContentTitle("Device Connected")
                        .setContentText("Listening for commands from dashboard...")
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .build();
            }
            startForeground(101, notification);
        }

        private void startPollingServer() {
            new Thread(() -> {
                while (isRunning) {
                    try {
                        URL url = new URL(SERVER_URL + "/api/poll");
                        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.setConnectTimeout(3000);

                        if (conn.getResponseCode() == 200) {
                            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                            StringBuilder response = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                response.append(line);
                            }
                            reader.close();

                            String jsonStr = response.toString().trim();
                            if (!jsonStr.equals("{}") && !jsonStr.isEmpty()) {
                                handleCommand(new JSONObject(jsonStr));
                            }
                        }
                        conn.disconnect();
                    } catch (Exception ignored) {}

                    try { Thread.sleep(2000); } catch (InterruptedException e) { break; }
                }
            }).start();
        }

        private void handleCommand(JSONObject json) {
            try {
                String action = json.optString("action", "");
                String data = json.optString("data", "");

                switch (action) {
                    case "MESSAGE":
                        // ফোনের স্ক্রিনে মেসেজ পপ-আপ করা (MainActivity ওপেন করে)
                        Intent dialogIntent = new Intent(this, MainActivity.class);
                        dialogIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        dialogIntent.putExtra("alert_message", data);
                        startActivity(dialogIntent);
                        break;

                    case "VIBRATE":
                        int seconds = Integer.parseInt(data);
                        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                        if (vibrator != null) {
                            vibrator.vibrate(seconds * 1000);
                        }
                        break;

                    case "RING":
                        int ringSeconds = Integer.parseInt(data);
                        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                        if (ringtone == null) {
                            ringtone = RingtoneManager.getRingtone(getApplicationContext(), alert);
                        }
                        if (ringtone != null && !ringtone.isPlaying()) {
                            ringtone.play();
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                if (ringtone != null && ringtone.isPlaying()) ringtone.stop();
                            }, ringSeconds * 1000);
                        }
                        break;

                    case "WALLPAPER":
                        new Thread(() -> {
                            try {
                                InputStream in = new URL(data).openStream();
                                Bitmap bitmap = BitmapFactory.decodeStream(in);
                                WallpaperManager wm = WallpaperManager.getInstance(getApplicationContext());
                                wm.setBitmap(bitmap);
                            } catch (Exception ignored) {}
                        }).start();
                        break;
                }
            } catch (Exception ignored) {}
        }

        @Override
        public IBinder onBind(Intent intent) { return null; }

        @Override
        public void onDestroy() {
            isRunning = false;
            super.onDestroy();
        }
    }
}