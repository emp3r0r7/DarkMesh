package com.geeksville.mesh.service;

import static ar.com.hjg.pngj.PngHelperInternal.debug;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.emp3r0r7.darkmesh.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class DistressService extends Service {

    private static final String TAG = DistressService.class.getSimpleName();
    private static final String WAKELOCK_TAG = TAG + "::WakeLockTag";
    private static final int NOTIFICATION_ID = 97;
    private static final String CHANNEL_ID = "distress_service_channel";
    private final AtomicBoolean taskRunning = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wakeLock;
    private CompletableFuture<?> task;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }

    /**
     * @noinspection BusyWait
     */
    public void sendDistress(
            String contactKey,
            String userInput,
            long interval,
            String myName,
            boolean includeName) {

        taskRunning.set(true);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

        task = CompletableFuture.runAsync(() -> {

            while (taskRunning.get()) {
                wakeLock.acquire(interval + 5_000);

                try {
                    debug("Sending distress beacon");

                    String currentTime = DateFormat.getTimeInstance(DateFormat.MEDIUM)
                            .format(new Date(System.currentTimeMillis()));

                    String str = "[BCN] ";
                    if (includeName) {
                        str += userInput + " @ " + myName + " " + currentTime;
                    } else {
                        str += userInput + " @ " + currentTime;
                    }

                    GlobalRadioMesh.sendMessage(str, contactKey, 0);
                    Thread.sleep(interval);
                } catch (Exception e) {
                    debug("An error occurred while distressing: " + e);
                } finally {
                    if (wakeLock.isHeld()) wakeLock.release();
                }
            }
        }, executor);
    }


    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Distress Beacon Scheduler",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Distress Beacon Running")
                .setContentText("Sending Distress Now..")
                .setSmallIcon(R.drawable.ic_filled_radioactive_24)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else
            Toast.makeText(this, "Unable to launch PlanMsgService, version unsupported!", Toast.LENGTH_LONG).show();

        String contactKey = intent.getStringExtra("contactKey");
        String userInput = intent.getStringExtra("userInput");
        String interval = intent.getStringExtra("interval");
        String myLongName = intent.getStringExtra("myLongName");
        boolean includeName = intent.getBooleanExtra("includeName", false);

        long intervalLong = 30L;

        if (!TextUtils.isEmpty(interval)) {
            intervalLong = Long.parseLong(interval);
        }

        intervalLong *= 1000;

        Log.d("DistressService", "Received extras: "
                + contactKey + ", " + userInput + ", interval=" + interval
                + ", myLongName=" + myLongName + ", includeName=" + includeName);

        sendDistress(contactKey, userInput, intervalLong, myLongName, includeName);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        taskRunning.set(false);

        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        executor.shutdownNow();

        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        stopForeground(true);
    }
}
