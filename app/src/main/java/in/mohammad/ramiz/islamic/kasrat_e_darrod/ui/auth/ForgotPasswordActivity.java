package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.ActivityForgotPasswordBinding;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.PasswordStrength;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.SimpleTextWatcher;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * P1 · Forgot password, three steps on one screen:
 * 1 email → POST /auth/forgot-password, 2 the 6-digit code, 3 new password →
 * POST /auth/reset-password (the server checks the code here, so a wrong or
 * expired code sends the user back to step 2 with the error).
 */
public class ForgotPasswordActivity extends BaseActivity {

    public static final String EXTRA_EMAIL = "email";
    private static final String STATE_STEP = "step", STATE_EMAIL = "sent_to";
    private static final long RESEND_MS = 30_000L;

    private ActivityForgotPasswordBinding b;
    private TextView[] boxes;
    private int step = 1;
    private String sentTo = "";
    private boolean busy;
    private CountDownTimer resendTimer;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Passwords, codes, account deletion: no screenshots, no tapjacking.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.security.ScreenGuard.protect(this);
        b = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.column, true);
        EdgeInsets.padTop(b.progress);
        boxes = new TextView[]{b.otp0, b.otp1, b.otp2, b.otp3, b.otp4, b.otp5};

        if (savedInstanceState != null) {
            step = savedInstanceState.getInt(STATE_STEP, 1);
            sentTo = savedInstanceState.getString(STATE_EMAIL, "");
        } else {
            String prefill = getIntent().getStringExtra(EXTRA_EMAIL);
            if (!TextUtils.isEmpty(prefill)) b.inputEmail.setText(prefill);
        }

        b.otpInput.addTextChangedListener(new SimpleTextWatcher(code -> {
            renderBoxes(code);
            if (code.length() == 6 && step == 2) goTo(3);
        }));
        b.otpInput.setOnFocusChangeListener((v, has) -> renderBoxes(code()));
        b.otpBoxes.setOnClickListener(v -> focusCode());
        StrengthMeter.attach(b.inputPassword, b.strengthRow, b.strengthBar, b.strengthLabel);
        b.inputEmail.setOnEditorActionListener((v, id, e) -> id == EditorInfo.IME_ACTION_DONE && primary());
        b.inputPassword.setOnEditorActionListener((v, id, e) -> id == EditorInfo.IME_ACTION_DONE && primary());
        b.btnPrimary.setOnClickListener(v -> primary());
        b.btnResend.setOnClickListener(v -> sendCode(sentTo));
        b.btnBack.setOnClickListener(v -> back());
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { back(); }
        });
        render();
    }

    // ── Steps ────────────────────────────────────────────────────────────────

    private boolean primary() {
        if (busy) return true;
        showError(null);
        if (step == 1) {
            String email = text(b.inputEmail);
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                b.inputEmailLayout.setError(getString(R.string.err_email));
                Haptics.reject(this);
                return true;
            }
            b.inputEmailLayout.setError(null);
            sendCode(email);
        } else if (step == 2) {
            if (code().length() != 6) {
                markBoxesError(true);
                showError(getString(R.string.err_code_required));
                Haptics.reject(this);
                return true;
            }
            goTo(3);
        } else {
            reset();
        }
        return true;
    }

    private void sendCode(String email) {
        setBusy(true);
        ApiClient.get(this).forgotPassword(new dto.ForgotPasswordRequest(email))
                .enqueue(new Callback<dto.OkResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.OkResponse> call,
                                           @NonNull Response<dto.OkResponse> response) {
                        setBusy(false);
                        if (!response.isSuccessful()) {
                            showError(getString(AuthSession.messageFor(ApiErrors.of(response), R.string.err_generic)));
                            return;
                        }
                        // Always "sent": the server never says whether the email exists.
                        sentTo = email;
                        b.otpInput.setText("");
                        startResendTimer();
                        goTo(2);
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.OkResponse> call, @NonNull Throwable t) {
                        setBusy(false);
                        showError(getString(R.string.err_network));
                    }
                });
    }

    private void reset() {
        String pw = b.inputPassword.getText() == null ? "" : b.inputPassword.getText().toString();
        if (!PasswordStrength.longEnough(pw)) {
            b.inputPasswordLayout.setError(getString(R.string.err_password_short));
            Haptics.reject(this);
            return;
        }
        b.inputPasswordLayout.setError(null);
        setBusy(true);
        ApiClient.get(this).resetPassword(new dto.ResetPasswordRequest(sentTo, code(), pw))
                .enqueue(new Callback<dto.OkResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.OkResponse> call,
                                           @NonNull Response<dto.OkResponse> response) {
                        setBusy(false);
                        if (response.isSuccessful()) {
                            Haptics.click(ForgotPasswordActivity.this);
                            Toast.makeText(ForgotPasswordActivity.this, R.string.fp_done, Toast.LENGTH_LONG).show();
                            finish();
                            return;
                        }
                        Haptics.reject(ForgotPasswordActivity.this);
                        ApiErrors.Failure f = ApiErrors.of(response);
                        int msg = AuthSession.messageFor(f, R.string.err_generic);
                        boolean codeProblem = msg == R.string.err_code_wrong || msg == R.string.err_code_expired
                                || msg == R.string.err_code_too_many;
                        if (codeProblem) {
                            b.otpInput.setText("");
                            goTo(2);
                            markBoxesError(true);
                        }
                        showError(getString(msg));
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.OkResponse> call, @NonNull Throwable t) {
                        setBusy(false);
                        showError(getString(R.string.err_network));
                    }
                });
    }

    private void back() {
        if (busy) return;
        if (step > 1) goTo(step - 1);
        else finish();
    }

    private void goTo(int next) {
        step = next;
        showError(null);
        render();
    }

    private void render() {
        b.stepTag.setText(getString(R.string.fp_step, step));
        b.stepEmail.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        b.stepCode.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        b.stepPassword.setVisibility(step == 3 ? View.VISIBLE : View.GONE);
        switch (step) {
            case 1:
                b.title.setText(R.string.fp_title_1);
                b.sub.setText(R.string.fp_sub_1);
                b.btnPrimary.setText(R.string.fp_send);
                break;
            case 2:
                b.title.setText(R.string.fp_title_2);
                b.sub.setText(getString(R.string.fp_sub_2, sentTo));
                b.btnPrimary.setText(R.string.action_continue);
                renderBoxes(code());
                focusCode();
                break;
            default:
                b.title.setText(R.string.fp_title_3);
                b.sub.setText(R.string.fp_sub_3);
                b.btnPrimary.setText(R.string.fp_reset);
                b.inputPassword.requestFocus();
                break;
        }
    }

    // ── Code tiles ───────────────────────────────────────────────────────────

    private String code() {
        return b.otpInput.getText() == null ? "" : b.otpInput.getText().toString();
    }

    private void renderBoxes(String code) {
        markBoxesError(false);
        for (int i = 0; i < boxes.length; i++) {
            boxes[i].setText(i < code.length() ? String.valueOf(code.charAt(i)) : "");
            boxes[i].setSelected(i == Math.min(code.length(), 5) && b.otpInput.hasFocus());
        }
    }

    private void markBoxesError(boolean on) {
        for (TextView box : boxes) box.setActivated(on);
    }

    private void focusCode() {
        b.otpInput.requestFocus();
        b.otpInput.setSelection(code().length());
        InputMethodManager imm = getSystemService(InputMethodManager.class);
        if (imm != null) imm.showSoftInput(b.otpInput, InputMethodManager.SHOW_IMPLICIT);
        renderBoxes(code());
    }

    private void startResendTimer() {
        if (resendTimer != null) resendTimer.cancel();
        b.btnResend.setEnabled(false);
        resendTimer = new CountDownTimer(RESEND_MS, 1000) {
            @Override public void onTick(long left) {
                b.btnResend.setText(getString(R.string.fp_resend_in, (int) Math.ceil(left / 1000.0)));
            }
            @Override public void onFinish() {
                b.btnResend.setEnabled(true);
                b.btnResend.setText(R.string.fp_resend);
            }
        }.start();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void setBusy(boolean on) {
        busy = on;
        b.progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        b.btnPrimary.setEnabled(!on);
        if (step == 1) b.btnPrimary.setText(on ? R.string.fp_sending : R.string.fp_send);
        if (step == 3) b.btnPrimary.setText(on ? R.string.fp_resetting : R.string.fp_reset);
    }

    private void showError(CharSequence message) {
        AuthSession.showError(b.error, message);
    }

    private static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_STEP, step);
        out.putString(STATE_EMAIL, sentTo);
    }

    @Override
    protected void onDestroy() {
        if (resendTimer != null) resendTimer.cancel();
        super.onDestroy();
    }
}
