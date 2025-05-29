package com.geeksville.mesh.ui.activity;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;

import com.emp3r0r7.darkmesh.R;
import com.geeksville.mesh.service.HuntHttpService;

public class HuntActivity extends Activity {

    //prefs name
    public static final String SHARED_HUNT_PREFS = "hunt_prefs";

    //prefs attributes
    public static final String HUNT_MODE = "hunting_mode";
    public static final String HUNT_DOMAIN = "hunt_domain";
    public static final String HUNT_TOKEN = "hunt_token";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hunt);

        SwitchCompat huntingSwitch = findViewById(R.id.huntingSwitch);
        EditText domainInput = findViewById(R.id.domainInput);
        EditText tokenInput = findViewById(R.id.tokenInput);
        Button validateButton = findViewById(R.id.validateButton);

        final SharedPreferences prefs = getSharedPreferences(SHARED_HUNT_PREFS, MODE_PRIVATE);
        final SharedPreferences.Editor editor = prefs.edit();
        final boolean isHuntingEnabled = prefs.getBoolean(HUNT_MODE, false);

        huntingSwitch.setChecked(isHuntingEnabled);
        domainInput.setEnabled(isHuntingEnabled);
        tokenInput.setEnabled(isHuntingEnabled);
        validateButton.setEnabled(isHuntingEnabled);

        domainInput.setText(prefs.getString(HUNT_DOMAIN, ""));
        tokenInput.setText(prefs.getString(HUNT_TOKEN, ""));

        huntingSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            domainInput.setEnabled(isChecked);
            tokenInput.setEnabled(isChecked);
            validateButton.setEnabled(isChecked);

            editor.putBoolean(HUNT_MODE, isChecked).apply();

        });

        validateButton.setOnClickListener(v -> {

            String domain = String.valueOf(domainInput.getText());
            String token = String.valueOf(tokenInput.getText());

            if(isHuntingEnabled && (domain.isEmpty() || token.isEmpty())) {
                Toast.makeText(this, "You must input both domain and token!", Toast.LENGTH_LONG).show();
                return;
            }

            HuntHttpService service = HuntHttpService.getInstance();

            service.checkHealthAsync(token, domain, new HuntHttpService.HuntCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> {

                        Toast.makeText(HuntActivity.this, "Health check OK! You are now a Hunter!", Toast.LENGTH_SHORT).show();

                        editor.putBoolean(HUNT_MODE, true);
                        editor.putString(HUNT_DOMAIN, domain);
                        editor.putString(HUNT_TOKEN, token);

                        editor.apply();

                        finish(); //end of activity!
                    });
                }

                @Override
                public void onFailure(Exception e) {
                    runOnUiThread(() ->
                            Toast.makeText(
                                    HuntActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                            );
                }
            });
        });
    }
}
