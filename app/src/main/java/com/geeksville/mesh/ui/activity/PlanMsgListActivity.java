package com.geeksville.mesh.ui.activity;

import static com.geeksville.mesh.prefs.UserPrefs.PlannedMessage.SHARED_PLANMSG_PREFS_STATUS;
import static com.geeksville.mesh.ui.activity.PlanMsgActivity.BROADCAST_ID_SIG;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.emp3r0r7.darkmesh.R;
import com.geeksville.mesh.database.entity.NodeEntity;
import com.geeksville.mesh.prefs.UserPrefs;
import com.geeksville.mesh.service.MeshService;
import com.geeksville.mesh.service.PlanMsgService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlanMsgListActivity extends AppCompatActivity {

    private static final String TAG = PlanMsgListActivity.class.getSimpleName();
    private MeshService meshService;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_planmsglist);

        Intent intent = new Intent(this, MeshService.class);
        intent.setAction(MeshService.BIND_LOCAL_ACTION_INTENT);
        bindService(intent, meshServiceConnection, Context.BIND_AUTO_CREATE);
    }

    private final ServiceConnection meshServiceConnection = new ServiceConnection() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            MeshService.MeshServiceAccessor accessor = (MeshService.MeshServiceAccessor) service;
            PlanMsgListActivity.this.meshService = accessor.getService();
            loadUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        if (meshService != null) loadUI();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unbindService(meshServiceConnection);
            Log.d(TAG, "Successfully unbound from MeshService");
        } catch (Exception e) {
            Log.w(TAG, "Tried to unbind but service was already unbound", e);
        }
    }

    @SuppressLint("SetTextI18n")
    private void loadUI() {

        Intent planMsgService = new Intent(this, PlanMsgService.class);

        ConcurrentHashMap<Integer, NodeEntity> nodeDb = meshService.getNodeDBbyNodeNum();
        ListView listView = findViewById(R.id.listViewNodes);
        SharedPreferences messageLogPrefs = getSharedPreferences(UserPrefs.PlannedMessage.SHARED_PLANNED_MSG_PREFS, MODE_PRIVATE);

        List<SpannableString> nodeEntries = new ArrayList<>();
        Map<String, ?> all = messageLogPrefs.getAll();

        for (Map.Entry<String, ?> entry : all.entrySet()) {
            try {
                String nodeId = entry.getKey();
                String value = (String) entry.getValue();
                String[] values = value.split("\n");
                boolean allEmpty = Arrays.stream(values).allMatch(TextUtils::isEmpty);

                if (values.length == 0 || allEmpty) continue;

                int ruleCount = value.split("\n").length;

                final SpannableString spannable;

                if (nodeId.contains(BROADCAST_ID_SIG)) {
                    String label = "Chan " + nodeId;

                    String[] split = nodeId.split("\\^");
                    String chanName = split[2];
                    label += " " + chanName;
                    int start = label.lastIndexOf(chanName);
                    int end = start + chanName.length();
                    label += " Regole " + ruleCount;
                    spannable = new SpannableString(label);

                    if (start >= 0 && end > start) {
                        spannable.setSpan(
                                new ForegroundColorSpan(Color.parseColor("#4CAF50")), // verde
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }

                } else {

                    String label = "Nodo " + nodeId;

                    NodeEntity node = nodeDb.get(Integer.parseInt(nodeId));
                    int start = -1, end = -1;

                    if (node != null) {
                        String nodeName = node.getUser().getLongName();
                        label += " " + nodeName;
                        start = label.indexOf(nodeName);
                        end = start + nodeName.length();
                    }

                    label += " Regole " + ruleCount;
                    spannable = new SpannableString(label);

                    if (start >= 0 && end > start) {
                        spannable.setSpan(
                                new ForegroundColorSpan(Color.parseColor("#4CAF50")), // verde
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        );
                    }
                }

                nodeEntries.add(spannable);

            } catch (Exception e) {
                Log.w(TAG, "Error parsing entry", e);
            }
        }

        if (nodeEntries.isEmpty()) {
            TextView title = findViewById(R.id.planListTitle);
            title.setText("Nessuna pianificazione effettuata.");
        }

        ArrayAdapter<SpannableString> adapter = new ArrayAdapter<>(
                PlanMsgListActivity.this,
                android.R.layout.simple_list_item_1,
                nodeEntries
        );

        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String selected = String.valueOf(nodeEntries.get(position));

            String nodeId = selected.split(" ")[1];
            Intent i = new Intent(PlanMsgListActivity.this, PlanMsgActivity.class);
            i.putExtra(PlanMsgActivity.NODE_ID_EXTRA_PARAM, nodeId);
            startActivity(i);

        });

        SwitchCompat serviceSwitch = findViewById(R.id.switchPlanning);

        SharedPreferences msgStatusPrefs = getSharedPreferences(SHARED_PLANMSG_PREFS_STATUS, MODE_PRIVATE);
        serviceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {

            msgStatusPrefs.edit().putBoolean(UserPrefs.PlannedMessage.PLANMSG_SERVICE_ACTIVE, isChecked).apply();
            handleListVisibility(listView, isChecked);

            if (isChecked) {
                handler.post(() ->
                        Toast.makeText(getApplicationContext(), "Pianificatore Messaggi Attivo!", Toast.LENGTH_SHORT).show());
                Log.d(TAG, "Message Plan Active");
                startService(planMsgService);
            } else {
                handler.post(() ->
                        Toast.makeText(getApplicationContext(), "Pianificatore Messaggi Disattivato!", Toast.LENGTH_SHORT).show());
                Log.d(TAG, "Message Plan deactivated");
                stopService(planMsgService);
            }
        });

        boolean serviceActive = msgStatusPrefs.getBoolean(UserPrefs.PlannedMessage.PLANMSG_SERVICE_ACTIVE, false);
        serviceSwitch.setChecked(serviceActive);
        handleListVisibility(listView, serviceActive);
    }

    private void handleListVisibility(ListView listView, boolean serviceActive) {
        listView.setEnabled(serviceActive);
        listView.setAlpha(serviceActive ? 1.0f : 0.5f);
    }
}



