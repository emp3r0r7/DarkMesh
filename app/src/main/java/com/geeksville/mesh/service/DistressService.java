package com.geeksville.mesh.service;

import static ar.com.hjg.pngj.PngHelperInternal.debug;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
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
import com.geeksville.mesh.model.UIViewModel;
import com.google.openlocationcode.OpenLocationCode;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class DistressService extends Service {

    private static final String TAG = DistressService.class.getSimpleName();

    public static final String PREF_STRESSTEST_ENABLED = "stress_test_enabled";
    public static final String PREF_STRESSTEST_PREFIX = "stress_test_prefix";
    public static final String PREF_STRESSTEST_DEFAULT_PREFIX = "[SOS]";

    //todo recheck this regex if we change pluscodes bit definition
    private static final Pattern PLUS_CODE_PATTERN =
            Pattern.compile("(?:[23456789CFGHJMPQRVWX]{2}){2,4}\\+");

    private static final String WAKELOCK_TAG = TAG + "::WakeLockTag";
    private static final int NOTIFICATION_ID = 97;
    private static final String CHANNEL_ID = "distress_service_channel";
    private final AtomicBoolean taskRunning = new AtomicBoolean(false);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private PowerManager.WakeLock wakeLock;
    private CompletableFuture<?> task;

    private static final int DEFAULT_COORDS_PRECISION = 8;

    private static volatile Double latitude = null;
    private static volatile Double longitude = null;
    private static volatile Integer altitude = null;

    private static volatile boolean livePosition = false;
    private static volatile boolean sendPositionToChat = false;
    private SharedPreferences uiPrefs;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        uiPrefs = UIViewModel.Companion.getPreferences(this);
    }

    public record DistressDTO (
            String contactKey,
            String userInput,
            long interval,
            String myName,
            boolean includeName,
            boolean includeGps,
            boolean includeTime
    ){}

    /**
     * @noinspection BusyWait
     */
    public void sendDistress(DistressDTO distressDTO) {

        taskRunning.set(true);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);

        var distressPrefix = uiPrefs.getString(
            PREF_STRESSTEST_PREFIX,
            PREF_STRESSTEST_DEFAULT_PREFIX
        );

        task = CompletableFuture.runAsync(() -> {

            while (taskRunning.get()) {
                wakeLock.acquire(distressDTO.interval + 5_000);

                try {
                    debug("Sending distress beacon");

                    StringBuilder sb = new StringBuilder(96);
                    sb.append(distressPrefix);

                    if (distressDTO.includeGps && latitude != null && longitude != null) {
                        var plus = coordinatesToPlusCode(
                                latitude,
                                longitude,
                                DEFAULT_COORDS_PRECISION
                        );

                        if (plus != null) {
                            sb.append(" ").append(plus);

                            if (altitude != null) {
                                sb.append(" A").append(altitude.intValue());
                            }
                        }
                    }

                    if (distressDTO.userInput != null && !distressDTO.userInput.isBlank()) {
                        sb.append(" ").append(distressDTO.userInput);
                    }

                    if (distressDTO.includeName && distressDTO.myName != null) {
                        sb.append(" ").append(distressDTO.myName);
                    }

                    if (distressDTO.includeTime) {
                        SimpleDateFormat sdf = new SimpleDateFormat("HHmm", Locale.ROOT);
                        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                        String time = sdf.format(new Date());
                        sb.append(" Z").append(time);
                    }

                    if(sb.toString().isBlank()){
                        sb.append("EMPTY Distress Beacon!");
                    }

                    GlobalRadioMesh.sendMessage(sb.toString(), distressDTO.contactKey, 0);
                    Thread.sleep(distressDTO.interval);

                } catch (Exception e) {
                    debug("SOS send failed: " + e.getMessage());
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
        boolean includeGps = intent.getBooleanExtra("includeGps", false);
        boolean includeTime = intent.getBooleanExtra("includeTime", false);

        long intervalLong = 30L;

        if (!TextUtils.isEmpty(interval)) {
            intervalLong = Long.parseLong(interval);
        }

        intervalLong *= 1000;

        Log.d("DistressService", "Received extras: "
                + contactKey + ", " + userInput + ", interval=" + interval
                + ", myLongName=" + myLongName + ", includeName=" + includeName);

        var distressDTO = new DistressDTO(
                contactKey,
                userInput,
                intervalLong,
                myLongName,
                includeName ,
                includeGps ,
                includeTime
        );

        sendDistress(distressDTO);

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


    @SuppressWarnings("SameParameterValue")
    public static String coordinatesToPlusCode(Double lat, Double lon, Integer precision) {
        try {
            return new OpenLocationCode(lat, lon, precision).getCode();
        } catch (Exception e){
            Log.e(TAG, "Could not encode coordinates: " + lat + " | " + lon);
            return null;
        }
    }

    @SuppressWarnings("unused") //maybe to be used in the future..
    public static OpenLocationCode.CodeArea plusCodeToCoordinates(String code) {
        return new OpenLocationCode(code).decode();
    }

    public static double[] plusCodeToCenter(String code) {
        try {
            OpenLocationCode.CodeArea area = new OpenLocationCode(code).decode();

            double lat = (area.getSouthLatitude() + area.getNorthLatitude()) / 2.0;
            double lon = (area.getWestLongitude() + area.getEastLongitude()) / 2.0;

            return new double[]{lat, lon};
        } catch (Exception e){
            Log.e(TAG, "Could not decode plus code: " + code);
            return null;
        }
    }

    public static String findValidPlusCode(String code) {
        if (TextUtils.isEmpty(code)) {
            return null;
        }
        var match = PLUS_CODE_PATTERN.matcher(code);
        if(match.find()){
            return match.group();
        }
        return null;
    }

    public static synchronized void setAltitude(Integer altitude) {
        DistressService.altitude = altitude;
    }

    public static synchronized void setLongitude(Double longitude) {
        DistressService.longitude = longitude;
    }

    public static synchronized void setLatitude(Double latitude) {
        DistressService.latitude = latitude;
    }

    public static synchronized Integer getAltitude() {
        return altitude;
    }

    public static synchronized Double getLongitude() {
        return longitude;
    }

    public static synchronized Double getLatitude() {
        return latitude;
    }

    public static synchronized boolean isLivePosition() {
        return livePosition;
    }

    public static synchronized void setLivePosition(boolean passGpsToDevice) {
        DistressService.livePosition = passGpsToDevice;
    }

    public static synchronized boolean isSendPositionToChat() {
        return sendPositionToChat;
    }

    public static synchronized void setSendPositionToChat(boolean sendPositionToChat) {
        DistressService.sendPositionToChat = sendPositionToChat;
    }

    public static synchronized void resetMessagePosition(){
        DistressService.latitude = null;
        DistressService.longitude = null;
        DistressService.altitude = null;

        DistressService.setSendPositionToChat(false);
        DistressService.setLivePosition(false);
    }
}
