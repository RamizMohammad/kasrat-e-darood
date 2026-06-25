package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Email/password sign-in. Authenticates against the live backend
 * (POST /api/v1/auth/login), stores the returned app JWT, then enters the app.
 */
public class LoginActivity extends AppCompatActivity {

    private EditText inputEmail;
    private EditText inputPassword;
    private MaterialButton signIn;
    private MaterialButton google;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        inputEmail = findViewById(R.id.input_email);
        inputPassword = findViewById(R.id.input_password);
        signIn = findViewById(R.id.btn_sign_in);
        google = findViewById(R.id.btn_google);

        signIn.setOnClickListener(v -> attemptLogin());

        // Google/Firebase sign-in is not configured on the server yet.
        google.setOnClickListener(v ->
                Toast.makeText(this, R.string.google_not_available, Toast.LENGTH_SHORT).show());

        findViewById(R.id.link_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void attemptLogin() {
        String email = inputEmail.getText().toString().trim();
        String password = inputPassword.getText().toString();

        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputEmail.setError(getString(R.string.error_invalid_email));
            inputEmail.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            inputPassword.setError(getString(R.string.error_password_required));
            inputPassword.requestFocus();
            return;
        }

        setLoading(true);
        ApiClient.get(this).login(new dto.EmailLoginRequest(email, password))
                .enqueue(new Callback<dto.LoginResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.LoginResponse> call,
                                           @NonNull Response<dto.LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            onAuthSuccess(response.body());
                        } else {
                            Toast.makeText(LoginActivity.this,
                                    ApiErrors.messageFrom(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.LoginResponse> call,
                                          @NonNull Throwable t) {
                        setLoading(false);
                        Toast.makeText(LoginActivity.this,
                                R.string.error_network, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void onAuthSuccess(dto.LoginResponse body) {
        TokenStore store = new TokenStore(this);
        String name = body.user != null ? body.user.displayName : null;
        String email = body.user != null ? body.user.email : null;
        String id = body.user != null ? body.user.id : null;
        store.saveSession(body.accessToken, body.refreshToken, id, name, email);

        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        finish();
    }

    private void setLoading(boolean loading) {
        signIn.setEnabled(!loading);
        google.setEnabled(!loading);
        signIn.setText(loading ? getString(R.string.signing_in) : getString(R.string.sign_in));
    }
}
