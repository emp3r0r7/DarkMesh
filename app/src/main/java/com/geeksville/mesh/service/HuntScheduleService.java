package com.geeksville.mesh.service;

import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_HUNT;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_HUNT_MODE;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_FAST;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_MEDIUM;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_SLOW;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_SUPER_SLOW;
import static com.geeksville.mesh.ui.activity.HuntActivity.SHARED_HUNT_PREFS;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.emp3r0r7.darkmesh.R;
import com.geeksville.mesh.database.entity.MyNodeEntity;
import com.geeksville.mesh.database.entity.NodeEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HuntScheduleService extends Service {

    public static final String HUNT_SCHEDULE_BIND_LOCAL_ACTION_INTENT = "com.emp3r0r7.mesh.MeshService.BIND_LOCAL";

    private static final String TAG = "HuntService";

    private static final String CHANNEL_ID = "hunt_service_channel";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private MeshService meshService;

    private SharedPreferences prefs;

    private SharedPreferences.Editor editor;

    private CompletableFuture<?> task;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(SHARED_HUNT_PREFS, MODE_PRIVATE);
        editor = prefs.edit();

        Intent intent = new Intent(this, MeshService.class);
        intent.setAction(HUNT_SCHEDULE_BIND_LOCAL_ACTION_INTENT);

        bindService(intent, meshServiceConnection, Context.BIND_AUTO_CREATE);
    }


    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Hunt Background Scan",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Background Hunt Active")
                .setContentText("Auto Trace Scan in progress..")
                .setSmallIcon(R.drawable.app_icon)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .build();

        startForeground(1, notification);

        return START_STICKY;
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            unbindService(meshServiceConnection);

            if (task != null && !task.isDone())
                task.cancel(true);

            executor.shutdownNow();
            stopForeground(true);

            Log.d(TAG, "Successfully unbound from MeshService");
        } catch (Exception e) {
            Log.w(TAG, "Tried to unbind but service was already unbound", e);
        }

        meshService = null;
        editor.putBoolean(BACKGROUND_HUNT, false).commit();

        Log.d(TAG, "HuntScheduleService destroyed");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private final ServiceConnection meshServiceConnection = new ServiceConnection() {
        /** @noinspection BusyWait*/
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MeshService.MeshServiceAccessor accessor = (MeshService.MeshServiceAccessor) service;
            HuntScheduleService.this.meshService = accessor.getService();
            editor.putBoolean(BACKGROUND_HUNT, true).commit();

            task = CompletableFuture.runAsync(() -> {

                Log.d(TAG, "Starting Traceroute loop task now..");
                MyNodeEntity myNodeEntity = meshService.getMyNodeInfo();

                while(prefs.getBoolean(BACKGROUND_HUNT, false)){

                    try {
                        ConcurrentHashMap<Integer, NodeEntity> db = meshService.getNodeDBbyNodeNum();
                        List<NodeEntity> nodes = new ArrayList<>(db.values());

                        nodes.sort((n1, n2) ->
                                Integer.compare(n2.getLastHeard(), n1.getLastHeard()));

                        Log.d("Hunt", "DB size: " + db.size());

                        for(NodeEntity node : nodes){

                            // avoiding self traceroute here!
                            if(myNodeEntity != null && myNodeEntity.getMyNodeNum() == node.getNum()) continue;

                            int packetId = meshService.getBinder().getPacketId();
                            Log.d(TAG, "Requesting traceroute for name " + node.getUser().getLongName());

                            meshService.getBinder().requestTraceroute(packetId, node.getNum());
                            long sleepRate = parseBackgroundScanMode();

                            Log.d(TAG, "Sleeping for : " + sleepRate + " millis before keeping on..");
                            Thread.sleep(sleepRate);
                        }

                    } catch (Exception e){
                        Log.e(TAG,"An error occurred while scanning local node database!");
                    }
                }

            }, executor);

            task.whenComplete((o, throwable) -> Log.d(TAG, "Task interrupted!"));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {}

    };

    private long parseBackgroundScanMode(){

        String scanMode = prefs.getString(BACKGROUND_HUNT_MODE, BACKGROUND_MODE_FAST);

        switch (scanMode){

            case BACKGROUND_MODE_FAST -> {
                return 31_000;
            }

            case BACKGROUND_MODE_MEDIUM -> {
                return 61_000;
            }

            case BACKGROUND_MODE_SLOW -> {
                return 121_000;
            }

            case BACKGROUND_MODE_SUPER_SLOW -> {
                return 300_000;
            }
        }

        return 45_000; //default value if none is found, but never occurs
    }

}
