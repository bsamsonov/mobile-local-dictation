package com.bvs.localdictation;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.ModelDownloadListener;
import android.speech.RecognitionService;
import android.speech.RecognitionSupport;
import android.speech.RecognitionSupportCallback;
import android.speech.SpeechRecognizer;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Setup + diagnostics screen: permission, enabling the IME, on-device model status/download. */
public class MainActivity extends Activity {

    private TextView setupStatus;
    private TextView diag;
    private TextView langStatus;
    private Spinner langSpinner;
    private EditText langField;
    private List<Languages.Entry> langEntries = Collections.emptyList();
    /** Spinner fires selection events when its adapter is set; only a touch makes a selection the user's choice. */
    private boolean langTouched;
    private SpeechRecognizer probe;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        col.setPadding(p, p, p, p);
        scroll.addView(col);

        col.addView(text(getString(R.string.intro), 14));

        col.addView(header(getString(R.string.h_setup)));
        setupStatus = text("", 14);
        col.addView(setupStatus);
        col.addView(button(getString(R.string.btn_grant_mic), v ->
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1)));
        col.addView(button(getString(R.string.btn_enable_ime), v ->
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))));
        col.addView(button(getString(R.string.btn_pick_ime), v -> imm().showInputMethodPicker()));

        col.addView(header(getString(R.string.h_language)));
        col.addView(text(getString(R.string.language_label), 14));
        langSpinner = new Spinner(this);
        langSpinner.setOnTouchListener((v, ev) -> { langTouched = true; return false; });
        langSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) { onLanguagePicked(pos); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        col.addView(langSpinner);
        // Fallback when the engine does not report its languages (Android < 13 or an error).
        langField = new EditText(this);
        langField.setSingleLine(true);
        langField.setHint(R.string.language_hint);
        langField.setText(Speech.language(this));
        langField.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) {
                // Only typing by the user counts (not state restore or the drop-down mirroring its pick).
                if (langField.hasFocus()) Speech.setLanguage(MainActivity.this, s.toString());
            }
        });
        langField.setSaveEnabled(false);
        langField.setVisibility(View.GONE);
        col.addView(langField);
        langStatus = text("", 14);
        col.addView(langStatus);
        col.addView(button(getString(R.string.btn_check_langs), v -> loadLanguages(true)));
        col.addView(button(getString(R.string.btn_download), v -> downloadModel()));

        CheckBox auto = new CheckBox(this);
        auto.setText(R.string.opt_autostart);
        auto.setChecked(Speech.autoStart(this));
        auto.setOnCheckedChangeListener((b, c) -> { Speech.setAutoStart(this, c); saved(); });
        col.addView(auto);
        CheckBox fmt = new CheckBox(this);
        fmt.setText(R.string.opt_formatting);
        fmt.setChecked(Speech.formatting(this));
        fmt.setOnCheckedChangeListener((b, c) -> { Speech.setFormatting(this, c); saved(); });
        col.addView(fmt);
        CheckBox cmds = new CheckBox(this);
        cmds.setText(R.string.opt_commands);
        cmds.setChecked(Speech.voiceCommands(this));
        cmds.setOnCheckedChangeListener((b, c) -> { Speech.setVoiceCommands(this, c); saved(); });
        col.addView(cmds);
        CheckBox dig = new CheckBox(this);
        dig.setText(R.string.opt_digits);
        dig.setChecked(Speech.digits(this));
        dig.setOnCheckedChangeListener((b, c) -> { Speech.setDigits(this, c); saved(); });
        col.addView(dig);
        CheckBox cont = new CheckBox(this);
        cont.setText(R.string.opt_continuous);
        cont.setChecked(Speech.continuousAudio(this));
        cont.setOnCheckedChangeListener((b, c) -> { Speech.setContinuousAudio(this, c); saved(); });
        col.addView(cont);

        col.addView(header(getString(R.string.h_try)));
        col.addView(text(getString(R.string.try_text), 14));
        EditText test = new EditText(this);
        test.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        test.setMinLines(4);
        test.setHint(R.string.try_hint);
        col.addView(test);

        col.addView(header(getString(R.string.h_diag)));
        diag = text("", 12);
        diag.setTextIsSelectable(true);
        col.addView(diag);

        // Fixed bottom bar: an obvious way out, always visible while scrolling.
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        Button done = button(getString(R.string.btn_done), v -> finish());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(16), dp(4), dp(16), dp(8));
        root.addView(done, lp);
        setContentView(root);
        if (getActionBar() != null) getActionBar().setDisplayHomeAsUpEnabled(true);
        loadLanguages(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    /** Settings are saved on every change; say so, since there is no Save button. */
    private void saved() {
        Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        refresh();
    }

    @Override
    protected void onDestroy() {
        if (probe != null) probe.destroy();
        super.onDestroy();
    }

    private void refresh() {
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean enabled = false;
        for (InputMethodInfo i : imm().getEnabledInputMethodList()) {
            if (i.getPackageName().equals(getPackageName())) enabled = true;
        }
        String current = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
        boolean active = current != null && current.startsWith(getPackageName() + "/");
        setupStatus.setText((mic ? "✅ " : "❌ ") + getString(R.string.setup_mic) + "\n"
                + (enabled ? "✅ " : "❌ ") + getString(R.string.setup_enabled) + "\n"
                + getString(active ? R.string.setup_active : R.string.setup_inactive));

        StringBuilder d = new StringBuilder();
        d.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append("), ")
                .append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        d.append("isRecognitionAvailable: ").append(SpeechRecognizer.isRecognitionAvailable(this)).append('\n');
        d.append("isOnDeviceRecognitionAvailable: ").append(SpeechRecognizer.isOnDeviceRecognitionAvailable(this)).append('\n');
        d.append("On-device service (firmware config): ").append(onDeviceServiceConfig()).append('\n');
        d.append("Default recognition service: ")
                .append(Settings.Secure.getString(getContentResolver(), "voice_recognition_service")).append('\n');
        d.append("Installed RecognitionService components:\n");
        List<ResolveInfo> services = getPackageManager().queryIntentServices(
                new Intent(RecognitionService.SERVICE_INTERFACE), 0);
        for (ResolveInfo r : services) {
            d.append("  • ").append(new ComponentName(r.serviceInfo.packageName, r.serviceInfo.name).flattenToShortString()).append('\n');
        }
        diag.setText(d.toString());
    }

    /** Value of the framework resource that createOnDeviceSpeechRecognizer() binds to. */
    private String onDeviceServiceConfig() {
        try {
            Resources sys = Resources.getSystem();
            int id = sys.getIdentifier("config_defaultOnDeviceSpeechRecognitionService", "string", "android");
            if (id == 0) return "(no resource)";
            String v = sys.getString(id);
            return v.isEmpty() ? "(empty)" : v;
        } catch (Exception e) {
            return "(" + e.getClass().getSimpleName() + ")";
        }
    }

    private SpeechRecognizer probe() {
        if (probe == null) probe = SpeechRecognizer.createOnDeviceSpeechRecognizer(this);
        return probe;
    }

    /** Fill the language drop-down from the engine's lists; showLists also prints the raw lists. */
    private void loadLanguages(boolean showLists) {
        if (Build.VERSION.SDK_INT < 33) { showLanguageField(getString(R.string.lang_needs_33)); return; }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            showLanguageField(getString(R.string.lang_no_ondevice));
            return;
        }
        langStatus.setText(R.string.lang_checking);
        probe().checkRecognitionSupport(Speech.recognizeIntent(this), getMainExecutor(),
                new RecognitionSupportCallback() {
                    @Override
                    public void onSupportResult(RecognitionSupport s) {
                        onLanguages(s, showLists);
                    }

                    @Override
                    public void onError(int error) {
                        showLanguageField(getString(R.string.lang_check_failed, Speech.errorName(error)));
                    }
                });
    }

    private void onLanguages(RecognitionSupport s, boolean showLists) {
        List<String> installed = s.getInstalledOnDeviceLanguages();
        List<String> pending = s.getPendingOnDeviceLanguages();
        List<String> supported = s.getSupportedOnDeviceLanguages();
        String current = Speech.language(this);
        List<Languages.Entry> entries = Languages.build(installed, pending, supported, current);
        if (entries.isEmpty()) { showLanguageField(getString(R.string.lang_none)); return; }

        langEntries = entries;
        Locale ui = getResources().getConfiguration().getLocales().get(0);
        List<String> labels = new ArrayList<>();
        for (Languages.Entry e : entries) {
            labels.add(stateIcon(e.state) + "  " + Languages.displayName(e.tag, ui) + " · " + e.tag);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        langSpinner.setAdapter(adapter);
        int sel = Languages.indexOf(entries, current);
        langSpinner.setSelection(Math.max(sel, 0), false);
        langSpinner.setVisibility(View.VISIBLE);
        langField.setVisibility(View.GONE);

        String status = stateLine(entries.get(Math.max(sel, 0)));
        if (showLists) {
            status = status + "\n\n" + getString(R.string.lang_lists, installed, pending, supported, s.getOnlineLanguages());
        }
        langStatus.setText(status);
    }

    private void onLanguagePicked(int pos) {
        if (pos < 0 || pos >= langEntries.size()) return;
        Languages.Entry e = langEntries.get(pos);
        if (!langTouched || Languages.norm(e.tag).equals(Languages.norm(Speech.language(this)))) return;
        Speech.setLanguage(this, e.tag);
        langField.setText(e.tag);
        saved();
        langStatus.setText(stateLine(e));
    }

    private String stateLine(Languages.Entry e) {
        String line = e.tag + ": " + getString(stateText(e.state));
        return e.state == Languages.State.SUPPORTED ? line + "\n" + getString(R.string.lang_needs_download) : line;
    }

    private void showLanguageField(String status) {
        langSpinner.setVisibility(View.GONE);
        langField.setVisibility(View.VISIBLE);
        langStatus.setText(status);
    }

    /** Short marker in the drop-down; the full state text is in the status line under it. */
    private static String stateIcon(Languages.State state) {
        switch (state) {
            case INSTALLED: return "✅";
            case PENDING: return "⏳";
            case SUPPORTED: return "⬇️";
            default: return "❌";
        }
    }

    private static int stateText(Languages.State state) {
        switch (state) {
            case INSTALLED: return R.string.lang_installed;
            case PENDING: return R.string.lang_pending;
            case SUPPORTED: return R.string.lang_supported;
            default: return R.string.lang_unsupported;
        }
    }

    private void downloadModel() {
        if (Build.VERSION.SDK_INT < 33) { langStatus.setText(R.string.lang_needs_33); return; }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            langStatus.setText(R.string.lang_no_ondevice);
            return;
        }
        Intent intent = Speech.recognizeIntent(this);
        if (Build.VERSION.SDK_INT >= 34) {
            langStatus.setText(R.string.dl_requesting);
            probe().triggerModelDownload(intent, getMainExecutor(), new ModelDownloadListener() {
                @Override public void onProgress(int completedPercent) { langStatus.setText(getString(R.string.dl_progress, completedPercent)); }
                @Override public void onSuccess() { loadLanguages(false); langStatus.setText(R.string.dl_success); }
                @Override public void onScheduled() { langStatus.setText(R.string.dl_scheduled); }
                @Override public void onError(int error) { langStatus.setText(getString(R.string.dl_error, Speech.errorName(error))); }
            });
        } else {
            probe().triggerModelDownload(intent);
            langStatus.setText(R.string.dl_sent);
        }
    }

    // ---------------------------------------------------------------- tiny UI helpers

    private InputMethodManager imm() { return (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE); }

    private TextView text(String s, int sp) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }

    private TextView header(String s) {
        TextView t = text(s, 18);
        t.setPadding(0, dp(20), 0, dp(4));
        return t;
    }

    private Button button(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
