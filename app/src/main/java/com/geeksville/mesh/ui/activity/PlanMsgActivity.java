package com.geeksville.mesh.ui.activity;

import android.annotation.SuppressLint;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.emp3r0r7.darkmesh.R;
import com.geeksville.mesh.database.entity.NodeEntity;
import com.geeksville.mesh.database.entity.QuickChatAction;
import com.geeksville.mesh.prefs.UserPrefs;
import com.geeksville.mesh.service.MeshService;
import com.geeksville.mesh.service.QuickChatBridge;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;


/**
 * @noinspection FieldCanBeLocal
 */
public class PlanMsgActivity extends AppCompatActivity {
    private static final String TAG = PlanMsgActivity.class.getSimpleName();
    public static final String NODE_ID_EXTRA_PARAM = "nodeId";
    public static final String PLAN_BINDER = "com.emp3r0r7.mesh.PlanMsgActivity.PLAN_BINDER";
    public static final String[] DAYS = {"LUN", "MAR", "MER", "GIO", "VEN", "SAB", "DOM"};
    public static final String SEPARATOR_DATE_MSG = "—"; //dash piu grande
    public static final String BROADCAST_ID_SIG = "^all^";
    private MeshService meshService;
    private String currentNodeId;

    private String currentNodeName;
    private Integer broadcastChannel = null;

    private SharedPreferences plannedMessagesPrefs, msgStatusPrefs;

    //ui
    private Spinner spinnerDay;
    private NDSpinner quickMessagesSpinner;
    private Button btnTime, btnAdd, btnSave;
    private EditText inputMessage;
    private ListView listRules;
    private int selectedHour = -1, selectedMinute = -1;
    private ArrayAdapter<String> rulesAdapter;
    private final List<String> rules = new ArrayList<>();
    private final List<String> removedRules = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_planmsg);

        plannedMessagesPrefs = getSharedPreferences(UserPrefs.PlannedMessage.SHARED_PLANNED_MSG_PREFS, MODE_PRIVATE);
        msgStatusPrefs = getSharedPreferences(UserPrefs.PlannedMessage.SHARED_PLANMSG_PREFS_STATUS, MODE_PRIVATE);

        currentNodeId = getIntent().getStringExtra(NODE_ID_EXTRA_PARAM);

        if (currentNodeId == null || currentNodeId.contains("Unknown Channel")) {
            Toast.makeText(this, "Unable to retrieve current node id", Toast.LENGTH_LONG).show();
            finish();
            return;
        } else if (currentNodeId.contains(BROADCAST_ID_SIG)) {
            String[] split = currentNodeId.split("\\^");
            // chan , type, name -> 4^all^NOME
            currentNodeName = split[2];
            broadcastChannel = Integer.parseInt(split[0]);
        }

        Intent intent = new Intent(this, MeshService.class);
        intent.setAction(MeshService.BIND_LOCAL_ACTION_INTENT);

        bindService(intent, meshServiceConnection, Context.BIND_AUTO_CREATE);
        loadUI();
    }

    @SuppressLint("SetTextI18n")
    private void loadUI() {

        boolean msgPlanStatus = msgStatusPrefs.getBoolean(UserPrefs.PlannedMessage.PLANMSG_SERVICE_ACTIVE, false);
        TextView statusView = findViewById(R.id.plannerStatus);

        statusView.setText("Stato Pianificazione Globale: " + (msgPlanStatus ? "ON" : "OFF"));
        statusView.setTextColor(getColor(msgPlanStatus ? android.R.color.holo_green_dark : android.R.color.holo_red_dark));

        spinnerDay = findViewById(R.id.spinnerDay);
        quickMessagesSpinner = findViewById(R.id.quick_messages);

        btnTime = findViewById(R.id.btnTime);
        inputMessage = findViewById(R.id.inputMessage);
        btnAdd = findViewById(R.id.btnAdd);
        btnSave = findViewById(R.id.btnSave);
        listRules = findViewById(R.id.listRules);

        // Giorni
        ArrayAdapter<String> daysAdapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, DAYS);
        spinnerDay.setAdapter(daysAdapter);

        // Lista regole
        rulesAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, rules);
        listRules.setAdapter(rulesAdapter);

        // Carica eventuali regole salvate
        loadRules();

        btnTime.setOnClickListener(v -> {
            Calendar now = Calendar.getInstance();
            int h = (selectedHour >= 0) ? selectedHour : now.get(Calendar.HOUR_OF_DAY);
            int m = (selectedMinute >= 0) ? selectedMinute : now.get(Calendar.MINUTE);
            new TimePickerDialog(this, (view, hourOfDay, minute) -> {
                selectedHour = hourOfDay;
                selectedMinute = minute;
                btnTime.setText(String.format(Locale.getDefault(), "Ora: %02d:%02d", hourOfDay, minute));
            }, h, m, true).show();
        });

        btnAdd.setOnClickListener(v -> {
            if (selectedHour < 0) {
                Toast.makeText(this, "Seleziona un'ora", Toast.LENGTH_SHORT).show();
                return;
            }
            String msg = inputMessage.getText().toString().trim();
            if (msg.isEmpty()) {
                Toast.makeText(this, "Inserisci un messaggio", Toast.LENGTH_SHORT).show();
                return;
            }
            String day = DAYS[spinnerDay.getSelectedItemPosition()];
            String row = String.format(Locale.getDefault(), "%s %02d:%02d " + SEPARATOR_DATE_MSG + " %s", day, selectedHour, selectedMinute, msg);
            rules.add(row);
            rulesAdapter.notifyDataSetChanged();
            // reset input minimi
            inputMessage.setText("");
        });

        // tap su item -> elimina veloce
        listRules.setOnItemClickListener((parent, view, position, id) -> {
            String removed = rules.remove(position);
            removedRules.add(removed);
            rulesAdapter.notifyDataSetChanged();
        });

        btnSave.setOnClickListener(v -> saveRules());
    }

    private void saveRules() {

        if (currentNodeName == null) {
            Toast.makeText(this, "Cannot save plan for null node!", Toast.LENGTH_LONG).show();
            return;
        }

        String joined = TextUtils.join("\n", rules);

        plannedMessagesPrefs.edit().putString(String.valueOf(currentNodeId), joined).apply();

        Toast.makeText(this, "Plan saved successfully for " + currentNodeName, Toast.LENGTH_LONG).show();

        // Invia ogni regola come broadcast

        if (rules.isEmpty()) {
            Intent intent = new Intent(PLAN_BINDER);
            intent.putExtra("nodeId", currentNodeId);
            intent.putExtra("clearAll", true);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
            return;
        }

        for (String r : rules)
            sendRuleBroadcast(r, 0);

        for (String r : removedRules)
            sendRuleBroadcast(r, 1);
    }

    private void sendRuleBroadcast(String rule, int removeRule) {
        try {
            // Esempio: "LUN 13:00 — Controllo posizione"
            String[] parts = rule.split(" ", 3); // [0]=giorno, [1]=ora:minuto, [2]=— messaggio
            if (parts.length < 3) return;

            Intent intent = generateIntent(parts, removeRule);
            sendBroadcast(intent);

        } catch (Exception e) {
            Log.w(TAG, "Error parsing plan: " + rule, e);
        }
    }

    @NonNull
    private Intent generateIntent(String[] parts, int toRemove) {
        String day = parts[0];
        String time = parts[1];
        String msg = parts[2].substring(parts[2].indexOf(SEPARATOR_DATE_MSG) + 1).trim();

        Intent intent = new Intent(PLAN_BINDER);
        intent.setPackage(getPackageName());

        intent.putExtra("nodeId", currentNodeId);
        intent.putExtra("day", day);
        intent.putExtra("time", time);
        intent.putExtra("msg", msg);
        intent.putExtra("toRemove", toRemove);
        return intent;
    }


    private void loadRules() {
        String joined = plannedMessagesPrefs.getString(currentNodeId, null);
        if (joined != null && !joined.isEmpty()) {
            String[] arr = joined.split("\n");
            rules.clear();
            Collections.addAll(rules, arr);
            if (rulesAdapter != null) rulesAdapter.notifyDataSetChanged();
        }
    }

    private final ServiceConnection meshServiceConnection = new ServiceConnection() {

        @SuppressLint("SetTextI18n")
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            MeshService.MeshServiceAccessor accessor = (MeshService.MeshServiceAccessor) service;

            if (broadcastChannel == null && currentNodeName == null) {
                PlanMsgActivity.this.meshService = accessor.getService();
                ConcurrentHashMap<Integer, NodeEntity> db = meshService.getNodeDBbyNodeNum();
                NodeEntity node = db.get(Integer.parseInt(currentNodeId));

                if (node == null) {
                    runOnUiThread(() ->
                            Toast.makeText(PlanMsgActivity.this, "Unable to retrieve current node name, falling back to id", Toast.LENGTH_LONG).show());
                    currentNodeName = String.valueOf(currentNodeId);
                } else {
                    currentNodeName = node.getUser().getLongName();
                }
            }

            List<QuickChatAction> quickChats = QuickChatBridge.getQuickChats(getApplicationContext());
            List<String> displayStrings = new ArrayList<>();
            List<String> messageOnlyList = new ArrayList<>();

            displayStrings.add("Seleziona un comando rapido...");
            messageOnlyList.add("");

            for (QuickChatAction action : quickChats) {
                displayStrings.add(action.getName() + ": " + action.getMessage());
                messageOnlyList.add(action.getMessage());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(
                    PlanMsgActivity.this,
                    android.R.layout.simple_spinner_item,
                    displayStrings
            );

            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            quickMessagesSpinner.setAdapter(adapter);
            quickMessagesSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if (position == 0) {
                        // Placeholder selezionato
                        inputMessage.setText("");
                    } else {
                        String selectedMessage = messageOnlyList.get(position);
                        String currentText = inputMessage.getText().toString().trim();

                        if (currentText.isEmpty() || !currentText.equals(selectedMessage)) {
                            inputMessage.setText(selectedMessage);
                        }
                    }
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });

            String chanDesc = "Canale " + currentNodeName;
            String nodeDesc = "Nodo " + currentNodeName;
            String description = "Pianificazione " + (broadcastChannel != null ? chanDesc : nodeDesc);

            ((TextView) findViewById(R.id.txtNodeId)).setText(description);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
        }
    };


    //inner class per override dello spinner
    public static class NDSpinner extends androidx.appcompat.widget.AppCompatSpinner {
        public NDSpinner(Context context) {
            super(context);
        }

        public NDSpinner(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public NDSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);
        }

        @Override
        public void setSelection(int position, boolean animate) {
            boolean same = position == getSelectedItemPosition();
            super.setSelection(position, animate);
            if (same && getOnItemSelectedListener() != null) {
                getOnItemSelectedListener()
                        .onItemSelected(this, getSelectedView(), position, getSelectedItemId());
            }
        }

        @Override
        public void setSelection(int position) {
            boolean same = position == getSelectedItemPosition();
            super.setSelection(position);
            if (same && getOnItemSelectedListener() != null) {
                getOnItemSelectedListener()
                        .onItemSelected(this, getSelectedView(), position, getSelectedItemId());
            }
        }
    }


    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            unbindService(meshServiceConnection);
            Log.d(TAG, "Successfully unbound from MeshService");
        } catch (Exception e) {
            Log.w(TAG, "Tried to unbind but service was already unbound", e);
        }

        Log.d(TAG, PlanMsgActivity.class.getSimpleName() + " destroyed");
    }

}
