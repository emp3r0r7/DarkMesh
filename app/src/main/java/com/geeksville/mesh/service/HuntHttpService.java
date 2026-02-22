package com.geeksville.mesh.service;

import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.geeksville.mesh.prefs.UserPrefs;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class HuntHttpService {

    private static final AtomicReference<HuntHttpService> instance = new AtomicReference<>();

    private static final String TAG = "HuntHTTP";

    private static final String HEALTH_ENDPOINT = "/api/health";
    private static final String MOBILE_ENDPOINT = "/api/mobile";

    public interface HuntCallback {
        void onSuccess();
        void onFailure(Exception e);
    }

    private HuntHttpService(){}

    public static HuntHttpService getInstance(){
        HuntHttpService currentInstance = instance.get();
        if (currentInstance != null) {
            return currentInstance;
        }

        HuntHttpService newInstance = new HuntHttpService();
        if (instance.compareAndSet(null, newInstance)) {
            return newInstance;
        }

        return instance.get();
    }

    private final OkHttpClient client = new OkHttpClient();

    public void checkHealthAsync(String token, String domain, HuntCallback callback) {
        Request request = new Request.Builder()
                .url(domain + HEALTH_ENDPOINT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                callback.onFailure(e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response){
                    if (response.isSuccessful()){
                        Log.d(TAG, "Health check success : " + response);
                        callback.onSuccess();

                    } else
                        callback.onFailure(new Exception("Code: " + response.code()));
                }
            }
        });
    }

    public void maybeSendDataJsonAsync(SharedPreferences huntPrefs, String jsonPayload) {

        boolean isHuntEnabled = huntPrefs.getBoolean(UserPrefs.Hunting.HUNT_MODE, false);

        if(!isHuntEnabled) return;

        String domain = huntPrefs.getString(UserPrefs.Hunting.HUNT_DOMAIN, null);
        String token = huntPrefs.getString(UserPrefs.Hunting.HUNT_TOKEN, null);

        if(domain == null || token == null) return;

        Request request = new Request.Builder()
                .url(domain + MOBILE_ENDPOINT)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonPayload, MediaType.parse("application/json")))
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG,"Could not forward payload data " + e);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (response) {
                    if (response.isSuccessful())
                        Log.d(TAG,"Success : " + response);
                    else
                        Log.e(TAG,"Failure : " + new Exception("HTTP " + response.code() + ": " + response.message()));
                }
            }
        });
    }
}
