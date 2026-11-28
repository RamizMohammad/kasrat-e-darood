package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.ActivityRegisterBinding;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.PasswordStrength;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * P1 · Create an email account (POST /auth/register). The language comes from
 * the first-run pick; "email taken" shows on the email field.
 */
public class RegisterActivity extends BaseActivity {

    public static final String EXTRA_EMAIL = "email";

    private ActivityRegisterBinding b;
    private boolean busy;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Passwords, codes, account deletion: no screenshots, no tapjacking.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.security.ScreenGuard.protect(this);
        b = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.column, true);
        EdgeInsets.padTop(b.progress);
        if (savedInstanceState == null) Motion.enterStack(b.column);

        String email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (!TextUtils.isEmpty(email)) b.inputEmail.setText(email);

        StrengthMeter.attach(b.inputPassword, b.strengthRow, b.strengthBar, b.strengthLabel);
        b.btnBack.setOnClickListener(v -> finish());
        b.linkLogin.setOnClickListener(v -> finish());
        b.btnCreate.setOnClickListener(v -> attempt());
        b.inputPassword.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { attempt(); return true; }
            return false;
        });
    }

    private void attempt() {
        if (busy) return;
        b.inputNameLayout.setError(null);
        b.inputEmailLayout.setError(null);
        b.inputPasswordLayout.setError(null);
        AuthSession.showError(b.error, null);

        String name = text(b.inputName);
        String email = text(b.inputEmail);
        String password = b.inputPassword.getText() == null ? "" : b.inputPassword.getText().toString();

        if (name.isEmpty()) {
            fieldError(b.inputNameLayout, b.inputName, R.string.err_name);
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            fieldError(b.inputEmailLayout, b.inputEmail, R.string.err_email);
            return;
        }
        if (!PasswordStrength.longEnough(password)) {
            fieldError(b.inputPasswordLayout, b.inputPassword, R.string.err_password_short);
            return;
        }

        setBusy(true);
        String lang = new AppPrefs(this).lang();
        ApiClient.get(this).register(new dto.RegisterRequest(email, password, name, lang))
                .enqueue(new Callback<dto.LoginResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.LoginResponse> call,
                                           @NonNull Response<dto.LoginResponse> response) {
                        setBusy(false);
                        if (response.isSuccessful() && response.body() != null) {
                            Haptics.click(RegisterActivity.this);
                            AuthSession.complete(RegisterActivity.this, response.body());
                            return;
                        }
                        Haptics.reject(RegisterActivity.this);
                        ApiErrors.Failure f = ApiErrors.of(response);
                        int msg = AuthSession.messageFor(f, R.string.err_generic);
                        if (msg == R.string.err_email_taken) {
                            fieldError(b.inputEmailLayout, b.inputEmail, msg);
                        } else {
                            AuthSession.showError(b.error, getString(msg));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.LoginResponse> call, @NonNull Throwable t) {
                        setBusy(false);
                        AuthSession.showError(b.error, getString(R.string.err_network));
                    }
                });
    }

    private void fieldError(com.google.android.material.textfield.TextInputLayout layout,
                            android.widget.EditText input, int message) {
        layout.setError(getString(message));
        input.requestFocus();
        Haptics.reject(this);
    }

    private void setBusy(boolean on) {
        busy = on;
        b.progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        b.btnCreate.setEnabled(!on);
        b.btnCreate.setText(on ? R.string.reg_creating : R.string.reg_create);
    }

    private static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
