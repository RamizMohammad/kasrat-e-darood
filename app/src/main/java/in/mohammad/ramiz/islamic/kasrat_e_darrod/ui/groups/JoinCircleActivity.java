package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.gson.Gson;

import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.SimpleTextWatcher;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Join a circle (Stitch join_circle_enter_code_teal): six code boxes fed by a hidden field,
 * Paste, Continue. Continue looks the code up (GET /groups/invite/{code}) and opens the
 * invite preview; an unknown code says so under the boxes.
 */
public class JoinCircleActivity extends BaseActivity {

    private static final int LENGTH = 6;

    private final TextView[] boxes = new TextView[LENGTH];
    private EditText input;
    private TextView error;
    private MaterialButton next;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_circle);
        EdgeInsets.padSystemBars(findViewById(R.id.root), true);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        int[] ids = {R.id.code_0, R.id.code_1, R.id.code_2, R.id.code_3, R.id.code_4, R.id.code_5};
        for (int i = 0; i < LENGTH; i++) boxes[i] = findViewById(ids[i]);
        input = findViewById(R.id.code_input);
        error = findViewById(R.id.code_error);
        next = findViewById(R.id.code_continue);

        input.addTextChangedListener(new SimpleTextWatcher(t -> render()));
        input.setOnEditorActionListener((v, action, e) -> {
            if (action == EditorInfo.IME_ACTION_DONE && clean().length() == LENGTH) {
                lookUp();
                return true;
            }
            return false;
        });
        findViewById(R.id.code_boxes).setOnClickListener(v -> focusInput());
        findViewById(R.id.code_paste).setOnClickListener(v -> paste());
        next.setOnClickListener(v -> lookUp());

        String prefill = getIntent().getStringExtra("code");
        if (prefill != null) input.setText(prefill);
        render();
        input.post(this::focusInput);
    }

    /** Upper-case letters and digits only. */
    @NonNull
    private String clean() {
        Editable e = input.getText();
        String s = e == null ? "" : e.toString().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
        return s.length() > LENGTH ? s.substring(0, LENGTH) : s;
    }

    private void render() {
        String code = clean();
        if (!code.equals(String.valueOf(input.getText()))) {
            input.setText(code);
            input.setSelection(code.length());
            return;
        }
        error.setVisibility(View.GONE);
        for (int i = 0; i < LENGTH; i++) {
            boxes[i].setText(i < code.length() ? String.valueOf(code.charAt(i)) : "");
            boxes[i].setActivated(false);
            boxes[i].setSelected(i == Math.min(code.length(), LENGTH - 1));
        }
        next.setEnabled(code.length() == LENGTH && !busy);
    }

    private void focusInput() {
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT);
    }

    private void paste() {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = cm != null ? cm.getPrimaryClip() : null;
        if (clip == null || clip.getItemCount() == 0) return;
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text == null) return;
        Haptics.tick(this);
        // A shared link or message may carry the code inside other text: take the last 6-char run.
        String all = text.toString().toUpperCase(Locale.ROOT);
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[A-Z0-9]{6}").matcher(all);
        String found = null;
        while (m.find()) found = m.group();
        input.setText(found != null ? found : all.replaceAll("[^A-Z0-9]", ""));
        input.setSelection(input.length());
    }

    private void lookUp() {
        String code = clean();
        if (code.length() != LENGTH || busy) return;
        setBusy(true);
        ApiClient.get(this).groupByCode(code).enqueue(new Callback<dto.GroupDetail>() {
            @Override
            public void onResponse(@NonNull Call<dto.GroupDetail> call, @NonNull Response<dto.GroupDetail> r) {
                if (isFinishing()) return;
                setBusy(false);
                if (r.isSuccessful() && r.body() != null) {
                    Haptics.click(JoinCircleActivity.this);
                    startActivity(JoinPreviewActivity.intent(JoinCircleActivity.this, code, r.body()));
                    finish();
                } else {
                    fail(r.code() == 404 ? getString(R.string.circles_join_not_found)
                            : Groups.error(JoinCircleActivity.this, r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.GroupDetail> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                setBusy(false);
                fail(getString(R.string.error_network));
            }
        });
    }

    private void fail(@NonNull String text) {
        Haptics.reject(this);
        for (TextView b : boxes) b.setActivated(true);
        error.setText(text);
        error.setVisibility(View.VISIBLE);
        error.announceForAccessibility(text);
    }

    private void setBusy(boolean on) {
        busy = on;
        next.setEnabled(!on && clean().length() == LENGTH);
        next.setText(on ? R.string.circles_join_looking : R.string.action_continue);
    }

    @NonNull
    public static Intent intent(@NonNull Context ctx, @Nullable String code) {
        return new Intent(ctx, JoinCircleActivity.class).putExtra("code", code);
    }

}
