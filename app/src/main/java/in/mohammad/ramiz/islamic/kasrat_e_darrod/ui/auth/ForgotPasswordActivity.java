package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Forgot-password flow: request an OTP by email, then reset the password with
 * the code + a new password.
 */
public class ForgotPasswordActivity extends AppCompatActivity {

    public static final String EXTRA_EMAIL = "email";

    private EditText email, code, password;
    private MaterialButton sendCode, reset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_forgot_password);

        email = findViewById(R.id.input_fp_email);
        code = findViewById(R.id.input_fp_code);
        password = findViewById(R.id.input_fp_password);
        sendCode = findViewById(R.id.btn_send_code);
        reset = findViewById(R.id.btn_reset);

        String prefill = getIntent().getStringExtra(EXTRA_EMAIL);
        if (!TextUtils.isEmpty(prefill)) email.setText(prefill);

        sendCode.setOnClickListener(v -> requestCode());
        reset.setOnClickListener(v -> doReset());
        findViewById(R.id.link_back_login).setOnClickListener(v -> finish());
    }

    private boolean validEmail(String e) {
        return !TextUtils.isEmpty(e) && Patterns.EMAIL_ADDRESS.matcher(e).matches();
    }

    private void requestCode() {
        String e = email.getText().toString().trim();
        if (!validEmail(e)) {
            email.setError(getString(R.string.error_invalid_email));
            email.requestFocus();
            return;
        }
        sendCode.setEnabled(false);
        sendCode.setText(R.string.sending);
        ApiClient.get(this).forgotPassword(new dto.ForgotPasswordRequest(e))
                .enqueue(new Callback<dto.OkResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.OkResponse> call,
                                           @NonNull Response<dto.OkResponse> response) {
                        sendCode.setEnabled(true);
                        sendCode.setText(R.string.send_code);
                        // Always positive — the server never reveals if the email exists.
                        Toast.makeText(ForgotPasswordActivity.this,
                                R.string.code_sent, Toast.LENGTH_LONG).show();
                        code.requestFocus();
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.OkResponse> call,
                                          @NonNull Throwable t) {
                        sendCode.setEnabled(true);
                        sendCode.setText(R.string.send_code);
                        Toast.makeText(ForgotPasswordActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void doReset() {
        String e = email.getText().toString().trim();
        String c = code.getText().toString().trim();
        String p = password.getText().toString();

        if (!validEmail(e)) {
            email.setError(getString(R.string.error_invalid_email));
            return;
        }
        if (TextUtils.isEmpty(c)) {
            code.setError(getString(R.string.error_code_required));
            code.requestFocus();
            return;
        }
        if (p.length() < 8) {
            password.setError(getString(R.string.error_password_short));
            password.requestFocus();
            return;
        }

        reset.setEnabled(false);
        reset.setText(R.string.resetting);
        ApiClient.get(this).resetPassword(new dto.ResetPasswordRequest(e, c, p))
                .enqueue(new Callback<dto.OkResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.OkResponse> call,
                                           @NonNull Response<dto.OkResponse> response) {
                        reset.setEnabled(true);
                        reset.setText(R.string.reset_password);
                        if (response.isSuccessful()) {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    R.string.password_reset_done, Toast.LENGTH_LONG).show();
                            finish();   // back to login to sign in with the new password
                        } else {
                            Toast.makeText(ForgotPasswordActivity.this,
                                    ApiErrors.messageFrom(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.OkResponse> call,
                                          @NonNull Throwable t) {
                        reset.setEnabled(true);
                        reset.setText(R.string.reset_password);
                        Toast.makeText(ForgotPasswordActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                    }
                });
    }
}
