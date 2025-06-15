package com.geeksville.mesh.service;

import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_HUNT;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_HUNT_MODE;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_FAST;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_MEDIUM;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_SLOW;
import static com.geeksville.mesh.ui.activity.HuntActivity.BACKGROUND_MODE_SUPER_SLOW;
import static com.geeksville.mesh.ui.activity.HuntActivity.SHARED_HUNT_PREFS;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.geeksville.mesh.database.entity.NodeEntity;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class HuntScheduleService extends Service {

    public static final String HUNT_SCHEDULE_BIND_LOCAL_ACTION_INTENT = "com.emp3r0r7.mesh.MeshService.BIND_LOCAL";

    private static final String TAG = "HuntService";

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

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            unbindService(meshServiceConnection);

            if (task != null && !task.isDone())
                task.cancel(true);

            executor.shutdownNow();

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

                while(prefs.getBoolean(BACKGROUND_HUNT, false)){

                    try {
                        ConcurrentHashMap<Integer, NodeEntity> db = meshService.getNodeDBbyNodeNum();

                        Log.d("Hunt", "DB size: " + db.size());

                        for(NodeEntity node : db.values()){
                            int packetId = meshService.getBinder().getPacketId();
                            Log.d(TAG, "Requesting traceroute for name " + node.getLongName());

                            meshService.getBinder().requestTraceroute(packetId, node.getNum());
                            long sleepLapse = parseBackgroundScanMode();

                            Log.d(TAG, "Sleeping for : " + sleepLapse + " millis before keeping on..");
                            Thread.sleep(sleepLapse);
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
