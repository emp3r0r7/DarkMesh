package com.geeksville.mesh.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.emp3r0r7.darkmesh.R;
import com.geeksville.mesh.MainActivity;
import com.geeksville.mesh.service.HuntHttpService;
import com.geeksville.mesh.service.HuntScheduleService;

import java.util.Arrays;
import java.util.List;

public class HuntActivity extends Activity {

    //prefs name
    public static final String SHARED_HUNT_PREFS = "hunt_prefs";

    //prefs attributes
    public static final String HUNT_MODE = "hunting_mode";
    public static final String BACKGROUND_HUNT = "background_hunt";
    public static final String HUNT_DOMAIN = "hunt_domain";
    public static final String HUNT_TOKEN = "hunt_token";
    public static final String BACKGROUND_HUNT_MODE = "background_hunt_mode";

    //background mode params
    public static final String BACKGROUND_MODE_FAST = "FAST";
    public static final String BACKGROUND_MODE_MEDIUM = "MEDIUM";
    public static final String BACKGROUND_MODE_SLOW = "SLOW";
    public static final String BACKGROUND_MODE_SUPER_SLOW = "SUPER_SLOW";

    private static final List<String> scanModes = Arrays.asList(
            BACKGROUND_MODE_FAST,
            BACKGROUND_MODE_MEDIUM,
            BACKGROUND_MODE_SLOW,
            BACKGROUND_MODE_SUPER_SLOW
    );

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hunt);

        final Intent huntService = new Intent(this, HuntScheduleService.class);

        SwitchCompat huntingSwitch = findViewById(R.id.huntingSwitch);
        SwitchCompat backgroundHuntSwitch = findViewById(R.id.huntingBackground);
        EditText domainInput = findViewById(R.id.domainInput);
        EditText tokenInput = findViewById(R.id.tokenInput);
        Button validateButton = findViewById(R.id.validateButton);

        final SharedPreferences prefs = getSharedPreferences(SHARED_HUNT_PREFS, MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        final boolean isHuntingEnabled = prefs.getBoolean(HUNT_MODE, false);
        final boolean backgroundHunt = prefs.getBoolean(BACKGROUND_HUNT, false);
        final String scanMode = prefs.getString(BACKGROUND_HUNT_MODE, BACKGROUND_MODE_FAST);

        Spinner modeSpinner = findViewById(R.id.huntSpeedSpinner);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                scanModes
        );

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        modeSpinner.setAdapter(adapter);

        int selectedIndex = scanModes.indexOf(scanMode);
        if (selectedIndex >= 0) {
            modeSpinner.setSelection(selectedIndex);
        }

        huntingSwitch.setChecked(isHuntingEnabled);
        backgroundHuntSwitch.setChecked(backgroundHunt);
        domainInput.setEnabled(isHuntingEnabled);
        tokenInput.setEnabled(isHuntingEnabled);
        validateButton.setEnabled(isHuntingEnabled);

        domainInput.setText(prefs.getString(HUNT_DOMAIN, ""));
        tokenInput.setText(prefs.getString(HUNT_TOKEN, ""));

        huntingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            domainInput.setEnabled(isChecked);
            tokenInput.setEnabled(isChecked);
            validateButton.setEnabled(isChecked);
            backgroundHuntSwitch.setEnabled(isChecked);

            editor.putBoolean(HUNT_MODE, isChecked).apply();

            if(!isChecked){ //disabling background hunt if global hunt is off!
                backgroundHuntSwitch.setChecked(false);
                editor.putBoolean(BACKGROUND_HUNT, false).apply();
                stopService(huntService);
            }

        });

        validateButton.setOnClickListener(v -> {

            String domain = String.valueOf(domainInput.getText());
            String token = String.valueOf(tokenInput.getText());
            String selectedBackgroundMode = modeSpinner.getSelectedItem().toString();

            editor.putString(BACKGROUND_HUNT_MODE, selectedBackgroundMode).apply();

            if(domain.isBlank() || token.isBlank()) {
                Toast.makeText(this, "You must input both domain and token!", Toast.LENGTH_LONG).show();
                return;
            }

            if(backgroundHuntSwitch.isChecked())
                startForegroundService(huntService);
            else
                stopService(huntService);

            HuntHttpService service = HuntHttpService.getInstance();

            service.checkHealthAsync(token, domain, new HuntHttpService.HuntCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {

                        Toast.makeText(HuntActivity.this, "Health check OK! You are now a Hunter!", Toast.LENGTH_SHORT).show();

                        editor.putBoolean(HUNT_MODE, true);
                        editor.putString(HUNT_DOMAIN, domain);
                        editor.putString(HUNT_TOKEN, token);

                        editor.commit();
                        Intent intent = new Intent(HuntActivity.this.getApplicationContext(), MainActivity.class);
                        startActivity(intent);
                        finish();
                    });
                }

                @Override
                public void onFailure(Exception e) {

                    runOnUiThread(() -> {

                        Toast.makeText(
                                HuntActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT
                        ).show();

                        huntingSwitch.setChecked(false);
                    });
                }
            });
        });
    }
}
