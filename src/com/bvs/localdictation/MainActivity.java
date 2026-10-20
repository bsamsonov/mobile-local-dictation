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
import android.view.View;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Setup + diagnostics screen: permission, enabling the IME, on-device model status/download. */
public class MainActivity extends Activity {

    private TextView setupStatus;
    private TextView diag;
    private TextView langStatus;
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
        EditText lang = new EditText(this);
        lang.setSingleLine(true);
        lang.setText(Speech.language(this));
        lang.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { Speech.setLanguage(MainActivity.this, s.toString()); }
        });
        col.addView(text(getString(R.string.language_label), 14));
        col.addView(lang);
        langStatus = text("", 14);
        col.addView(langStatus);
        col.addView(button(getString(R.string.btn_check_langs), v -> checkSupport()));
        col.addView(button(getString(R.string.btn_download), v -> downloadModel()));

        CheckBox auto = new CheckBox(this);
        auto.setText(R.string.opt_autostart);
        auto.setChecked(Speech.autoStart(this));
        auto.setOnCheckedChangeListener((b, c) -> Speech.setAutoStart(this, c));
        col.addView(auto);
        CheckBox fmt = new CheckBox(this);
        fmt.setText(R.string.opt_formatting);
        fmt.setChecked(Speech.formatting(this));
        fmt.setOnCheckedChangeListener((b, c) -> Speech.setFormatting(this, c));
        col.addView(fmt);
        CheckBox cmds = new CheckBox(this);
        cmds.setText(R.string.opt_commands);
        cmds.setChecked(Speech.voiceCommands(this));
        cmds.setOnCheckedChangeListener((b, c) -> Speech.setVoiceCommands(this, c));
        col.addView(cmds);
        CheckBox dig = new CheckBox(this);
        dig.setText(R.string.opt_digits);
        dig.setChecked(Speech.digits(this));
        dig.setOnCheckedChangeListener((b, c) -> Speech.setDigits(this, c));
        col.addView(dig);
        CheckBox cont = new CheckBox(this);
        cont.setText(R.string.opt_continuous);
        cont.setChecked(Speech.continuousAudio(this));
        cont.setOnCheckedChangeListener((b, c) -> Speech.setContinuousAudio(this, c));
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

        setContentView(scroll);
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

    private void checkSupport() {
        if (Build.VERSION.SDK_INT < 33) { langStatus.setText(R.string.lang_needs_33); return; }
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            langStatus.setText(R.string.lang_no_ondevice);
            return;
        }
        langStatus.setText(R.string.lang_checking);
        probe().checkRecognitionSupport(Speech.recognizeIntent(this), getMainExecutor(),
                new RecognitionSupportCallback() {
                    @Override
                    public void onSupportResult(RecognitionSupport s) {
                        String lang = Speech.language(MainActivity.this);
                        boolean installed = containsLang(s.getInstalledOnDeviceLanguages(), lang);
                        boolean pending = containsLang(s.getPendingOnDeviceLanguages(), lang);
                        boolean supported = containsLang(s.getSupportedOnDeviceLanguages(), lang);
                        int state = installed ? R.string.lang_installed : pending ? R.string.lang_pending
                                : supported ? R.string.lang_supported : R.string.lang_unsupported;
                        langStatus.setText(lang + ": " + getString(state) + "\n\n"
                                + getString(R.string.lang_lists, s.getInstalledOnDeviceLanguages(),
                                s.getPendingOnDeviceLanguages(), s.getSupportedOnDeviceLanguages(),
                                s.getOnlineLanguages()));
                    }

                    @Override
                    public void onError(int error) {
                        langStatus.setText(getString(R.string.lang_check_failed, Speech.errorName(error)));
                    }
                });
    }

    private static boolean containsLang(List<String> list, String lang) {
        if (list == null) return false;
        String want = lang.replace('_', '-').toLowerCase();
        String wantBase = want.split("-")[0];
        for (String l : list) {
            String x = l.replace('_', '-').toLowerCase();
            if (x.equals(want) || x.equals(wantBase)) return true;
        }
        return false;
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
                @Override public void onSuccess() { langStatus.setText(R.string.dl_success); }
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
