package com.geeksville.mesh.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
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
import com.geeksville.mesh.prefs.UserPrefs;
import com.geeksville.mesh.service.HuntHttpService;
import com.geeksville.mesh.service.HuntScheduleService;

import java.util.Arrays;
import java.util.List;

public class HuntActivity extends Activity {

    private static final List<String> scanModes = Arrays.asList(
            UserPrefs.Hunting.BACKGROUND_MODE_FAST,
            UserPrefs.Hunting.BACKGROUND_MODE_MEDIUM,
            UserPrefs.Hunting.BACKGROUND_MODE_SLOW,
            UserPrefs.Hunting.BACKGROUND_MODE_SUPER_SLOW
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

        final SharedPreferences prefs = getSharedPreferences(UserPrefs.Hunting.SHARED_HUNT_PREFS, MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        final boolean isHuntingEnabled = prefs.getBoolean(UserPrefs.Hunting.HUNT_MODE, false);
        final boolean backgroundHunt = prefs.getBoolean(UserPrefs.Hunting.BACKGROUND_HUNT, false);
        final String scanMode = prefs.getString(UserPrefs.Hunting.BACKGROUND_HUNT_MODE, UserPrefs.Hunting.BACKGROUND_MODE_FAST);

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

        domainInput.setText(prefs.getString(UserPrefs.Hunting.HUNT_DOMAIN, ""));
        tokenInput.setText(prefs.getString(UserPrefs.Hunting.HUNT_TOKEN, ""));

        huntingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            domainInput.setEnabled(isChecked);
            tokenInput.setEnabled(isChecked);
            validateButton.setEnabled(isChecked);
            backgroundHuntSwitch.setEnabled(isChecked);

            editor.putBoolean(UserPrefs.Hunting.HUNT_MODE, isChecked).apply();

            if(!isChecked){ //disabling background hunt if global hunt is off!
                backgroundHuntSwitch.setChecked(false);
                editor.putBoolean(UserPrefs.Hunting.BACKGROUND_HUNT, false).apply();
                stopService(huntService);
            }

        });

        validateButton.setOnClickListener(v -> {

            String domain = String.valueOf(domainInput.getText());
            String token = String.valueOf(tokenInput.getText());
            String selectedBackgroundMode = modeSpinner.getSelectedItem().toString();

            editor.putString(UserPrefs.Hunting.BACKGROUND_HUNT_MODE, selectedBackgroundMode).apply();

            if(domain.isBlank() || token.isBlank()) {
                Toast.makeText(this, "You must input both domain and token!", Toast.LENGTH_LONG).show();
                return;
            }

            if(backgroundHuntSwitch.isChecked())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    startForegroundService(huntService);
                else
                    Toast.makeText(this, "Unable to launch Hunt Foreground Service, version unsupported!", Toast.LENGTH_LONG).show();
            else
                stopService(huntService);

            HuntHttpService service = HuntHttpService.getInstance();

            service.checkHealthAsync(token, domain, new HuntHttpService.HuntCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {

                        Toast.makeText(HuntActivity.this, "Health check OK! You are now a Hunter!", Toast.LENGTH_SHORT).show();

                        editor.putBoolean(UserPrefs.Hunting.HUNT_MODE, true);
                        editor.putString(UserPrefs.Hunting.HUNT_DOMAIN, domain);
                        editor.putString(UserPrefs.Hunting.HUNT_TOKEN, token);

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
