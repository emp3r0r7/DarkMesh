package com.geeksville.mesh.service;

import static com.geeksville.mesh.ui.activity.PlanMsgActivity.BROADCAST_ID_SIG;
import static com.geeksville.mesh.ui.activity.PlanMsgActivity.SEPARATOR_DATE_MSG;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.emp3r0r7.darkmesh.R;
import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.database.entity.NodeEntity;
import com.geeksville.mesh.prefs.UserPrefs;
import com.geeksville.mesh.ui.activity.PlanMsgActivity;

import java.util.Calendar;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlanMsgService extends Service {

    private static final String TAG = PlanMsgService.class.getSimpleName();
    private static final String WAKELOCK_TAG = TAG + "::WakeLockTag";
    private static final String CHANNEL_ID = "planmsg_service_channel";
    private static final int NOTIFICATION_ID = 99;
    private static final Set<String> SENT_TODAY = ConcurrentHashMap.newKeySet(); //static cross istanze
    private final Map<String, ScheduledMsg> taskMap = new ConcurrentHashMap<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MeshService meshService;
    private CompletableFuture<?> task;
    private PowerManager.WakeLock wakeLock;
    private SharedPreferences msgStatusPrefs;
    @Override
    public void onCreate() {
        super.onCreate();

        Intent intent = new Intent(this, MeshService.class);
        intent.setAction(MeshService.BIND_LOCAL_ACTION_INTENT);
        bindService(intent, meshServiceConnection, Context.BIND_AUTO_CREATE);
        msgStatusPrefs = getSharedPreferences(UserPrefs.PlannedMessage.SHARED_PLANMSG_PREFS_STATUS, MODE_PRIVATE);

        IntentFilter filter = new IntentFilter(PlanMsgActivity.PLAN_BINDER);
        ContextCompat.registerReceiver(
                this,
                scheduleReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
        );

        initMap();
    }

    public void startPollingThread() {

        final int pollRate = 15_000;

        task = CompletableFuture.runAsync(() -> {

            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            long clockNext = SystemClock.elapsedRealtime();

            while (msgStatusPrefs.getBoolean(UserPrefs.PlannedMessage.PLANMSG_SERVICE_ACTIVE, false)) {

                long epochNow = System.currentTimeMillis();
                long clockNow = SystemClock.elapsedRealtime();

                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
                wakeLock.acquire(pollRate + 5_000);

                try {
                    //sistema per evitare drift temporale
                    if (clockNow < clockNext) {
                        //noinspection BusyWait
                        Thread.sleep(clockNext - clockNow);
                    } else {
                        if (clockNow - clockNext > pollRate) {
                            Log.w(TAG, "Polling loop lagging. Resynchronizing timer.");
                            clockNext = clockNow;
                        }
                    }

                    if (meshService == null || meshService.getConnectionState() == MeshService.ConnectionState.DISCONNECTED) {
                        Log.d(TAG, "MeshService not ready");
                    } else {

                        Calendar now = Calendar.getInstance();

                        for (Map.Entry<String, ScheduledMsg> entry : taskMap.entrySet()) {

                            ScheduledMsg m = entry.getValue();
                            String sentKey = entry.getKey();

                            if (shouldSend(now, m.day, m.time)) {

                                if (SENT_TODAY.contains(sentKey)) continue;

                                String contactKey;
                                String readableDestination;

                                if (m.nodeId.contains(BROADCAST_ID_SIG)) {
                                    contactKey = m.nodeId;
                                    readableDestination = contactKey.split("\\^")[2];
                                } else {
                                    ConcurrentHashMap<Integer, NodeEntity> db = meshService.getNodeDBbyNodeNum();
                                    NodeEntity entity = db.get(Integer.parseInt(m.nodeId()));
                                    if (entity == null) continue;
                                    contactKey = meshService.buildContactKeyForMessage(entity);
                                    readableDestination = entity.getUser().getLongName();
                                }

                                SENT_TODAY.add(sentKey);
                                boolean ok = sendMessage(m.msg(), contactKey);

                                if (ok) {
                                    handler.post(() ->
                                            Toast.makeText(getApplicationContext(), "Planned message sent to " + readableDestination, Toast.LENGTH_SHORT).show());

                                    Log.d(TAG, "Sent planned message: " + m + " to " + readableDestination);
                                } else {
                                    handler.post(() ->
                                            Toast.makeText(getApplicationContext(), "Could not send message to " + readableDestination, Toast.LENGTH_SHORT).show());
                                }
                            }
                        }

                        if (now.get(Calendar.HOUR_OF_DAY) == 0 && now.get(Calendar.MINUTE) == 1) {
                            SENT_TODAY.clear();
                            Log.d(TAG, "Daily scheduled sent message count reset");
                        }
                    }

                } catch (Exception e) {
                    if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        Log.w(TAG, "Thread has been interrupted due to service stop!");
                        break;

                    } else
                        Log.e(TAG, "An error occurred in the polling loop", e);

                } finally {
                    if (wakeLock.isHeld()) wakeLock.release();
                }

                clockNext += pollRate;

                Log.d(TAG, TAG + " loop elapsed millis: " +
                        (System.currentTimeMillis() - epochNow)
                );
            }
        }, executor);
    }

    public boolean shouldSend(Calendar now, String day, String time) {
        String today = PlanMsgActivity.DAYS[(now.get(Calendar.DAY_OF_WEEK) + 5) % 7]; // DOM=1 → "DOM"=6

        if (!day.equals(today)) return false;

        String[] parts = time.split(":");
        int scheduledHour = Integer.parseInt(parts[0]);
        int scheduledMinute = Integer.parseInt(parts[1]);

        int nowHour = now.get(Calendar.HOUR_OF_DAY);
        int nowMinute = now.get(Calendar.MINUTE);

        int scheduledTotalMinutes = scheduledHour * 60 + scheduledMinute;
        int nowTotalMinutes = nowHour * 60 + nowMinute;

        int diff = nowTotalMinutes - scheduledTotalMinutes;
        return diff >= 0 && diff <= 15; //tolleranza di +15 min dell invio (utile in background)
    }


    @SuppressWarnings("unused")
    private void removePlanFromSharedPref(ScheduledMsg m) {

        SharedPreferences prefs = getSharedPreferences(UserPrefs.PlannedMessage.SHARED_PLANNED_MSG_PREFS, MODE_PRIVATE);
        String key = String.valueOf(m.nodeId());
        String joined = prefs.getString(key, null);
        if (joined == null) return;

        String toRemove = m.toString();
        StringBuilder sb = new StringBuilder();
        for (String r : joined.split("\n")) {
            if (!r.trim().equals(toRemove)) {
                sb.append(r).append("\n");
            }
        }

        String updated = sb.toString().trim();
        if (updated.isEmpty()) {
            prefs.edit().remove(key).apply();
        } else {
            prefs.edit().putString(key, updated).apply();
        }
    }

    private void initMap() {
        SharedPreferences prefs = getSharedPreferences(UserPrefs.PlannedMessage.SHARED_PLANNED_MSG_PREFS, MODE_PRIVATE);
        Map<String, ?> all = prefs.getAll();
        if (all.isEmpty()) return;

        for (Map.Entry<String, ?> entry : all.entrySet()) {

            String nodeId = entry.getKey();
            String joined = (String) entry.getValue();
            if (joined == null || joined.trim().isEmpty()) continue;

            String[] rows = joined.split("\n");
            for (String r : rows) {
                try {
                    // Es: "LUN 13:00 — Messaggio"
                    int firstSpace = r.indexOf(' ');
                    int dashIndex = r.indexOf(SEPARATOR_DATE_MSG);

                    if (firstSpace == -1 || dashIndex == -1 || dashIndex < firstSpace) {
                        Log.w(TAG, "Invalid format: " + r);
                        continue;
                    }

                    String day = r.substring(0, firstSpace).trim();
                    String time = r.substring(firstSpace + 1, dashIndex).trim();
                    String msg = r.substring(dashIndex + 1).trim();

                    ScheduledMsg m = new ScheduledMsg(nodeId, day, time, msg, 0);
                    taskMap.putIfAbsent(m.key(), m);
                    Log.d(TAG, "Loaded prefs content: " + m);

                } catch (Exception e) {
                    Log.w(TAG, "Error in parsing line: " + r, e);
                }
            }
        }
    }

    private final BroadcastReceiver scheduleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            String nodeId = intent.getStringExtra("nodeId");
            boolean clearAll = intent.getBooleanExtra("clearAll", false);

            if (clearAll && nodeId != null) {
                Log.d(TAG, "Plans are empty, removing all tasks..");
                for (Map.Entry<String, ScheduledMsg> entry : taskMap.entrySet())
                    if (entry.getKey().contains(nodeId)) {
                        taskMap.remove(entry.getKey());
                        Log.d(TAG, "Removed Key from TaskMap: " + entry.getKey());
                    }

            } else {

                String day = intent.getStringExtra("day");
                String time = intent.getStringExtra("time");
                String msg = intent.getStringExtra("msg");
                int toRemove = intent.getIntExtra("toRemove", -1);

                if (nodeId == null || day == null || time == null || msg == null || toRemove < 0) {
                    Log.w(TAG, "Invalid planned message received, ignoring.");
                    return;
                }

                ScheduledMsg m = new ScheduledMsg(nodeId, day, time, msg, toRemove);
                if (toRemove == 0) {
                    taskMap.put(m.key(), m);
                    Log.d(TAG, "Message received and planned : " + m);
                } else {
                    taskMap.remove(m.key());
                    Log.d(TAG, "Message received and removed from plan : " + m);
                }
            }
        }
    };

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "PlanMsgService Scheduler",
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
                .setContentTitle("Message Planner Running")
                .setContentText("Ready to send messages..")
                .setSmallIcon(R.drawable.ic_twotone_send_24)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE);
        else
            Toast.makeText(this, "Unable to launch PlanMsgService, version unsupported!", Toast.LENGTH_LONG).show();

        return START_STICKY;
    }

    private final ServiceConnection meshServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            MeshService.MeshServiceAccessor accessor = (MeshService.MeshServiceAccessor) service;
            PlanMsgService.this.meshService = accessor.getService();
            startPollingThread();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Log.w(TAG, "Service disconnected " + componentName);
        }
    };

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            unbindService(meshServiceConnection);

            if (task != null && !task.isDone()) {
                task.cancel(true);
            }

            executor.shutdownNow();

            if (wakeLock.isHeld()) {
                wakeLock.release();
            }

            stopForeground(true);
            Log.d(TAG, "Successfully unbound from MeshService");
            unregisterReceiver(scheduleReceiver);
        } catch (Exception e) {
            Log.w(TAG, "Tried to unbind but service was already unbound", e);
        }

        meshService = null;

        Log.d(TAG, TAG + " destroyed");
    }

    public boolean sendMessage(String str, String contactKey) throws RemoteException {
        // contactKey: unique contact key filter (channel)+(nodeId)
        //DataPacket(to=^all, bytes=[70], dataType=1, from=^local, time=1759587741442, id=0, status=UNKNOWN, hopLimit=0, channel=4)  <- channel
        Integer channel = null;

        if (contactKey != null && !contactKey.isEmpty()) {
            char c = contactKey.charAt(0);
            if (Character.isDigit(c)) {
                channel = Character.getNumericValue(c);
            }
        }

        String dest;

        if (channel != null && contactKey.contains(BROADCAST_ID_SIG)) {
            dest = "^" + contactKey.split("\\^")[1];
        } else if (channel != null) {
            dest = contactKey.substring(1);
        } else {
            dest = contactKey;
        }

        DataPacket p = new DataPacket(dest, (channel != null) ? channel : 0, str, 0);

        if (GlobalRadioMesh.getRadio() != null) {
            GlobalRadioMesh.getRadio().send(p);
            return true;
        } else {
            Log.d(TAG, "Could not send message radio mesh is null!");
            return false;
        }
    }

    /**
     * @param day  "LUN", "MAR", ...
     * @param time "13:00"
     */
    public record ScheduledMsg(String nodeId, String day, String time, String msg, int toRemove) {
        public String key() {
            return day + " " + time + "@" + nodeId;
        }

        @NonNull
        @Override
        public String toString() {
            return day + " " + time + " " + SEPARATOR_DATE_MSG + " " + msg;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
