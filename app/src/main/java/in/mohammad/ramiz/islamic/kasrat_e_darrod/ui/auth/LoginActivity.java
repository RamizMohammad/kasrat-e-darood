package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Log;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.KeyboardInsets;
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
    private GoogleSignInClient googleClient;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> handleGoogleResult(
                            GoogleSignIn.getSignedInAccountFromIntent(result.getData())));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        KeyboardInsets.attach(this);

        inputEmail = findViewById(R.id.input_email);
        inputPassword = findViewById(R.id.input_password);
        signIn = findViewById(R.id.btn_sign_in);
        google = findViewById(R.id.btn_google);

        GoogleSignInOptions options = new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, options);

        setupPasswordToggle();

        signIn.setOnClickListener(v -> attemptLogin());
        google.setOnClickListener(v -> startGoogleSignIn());

        findViewById(R.id.link_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));

        findViewById(R.id.link_forgot).setOnClickListener(v -> {
            Intent i = new Intent(this, ForgotPasswordActivity.class);
            i.putExtra(ForgotPasswordActivity.EXTRA_EMAIL,
                    inputEmail.getText().toString().trim());
            startActivity(i);
        });
    }

    private boolean passwordVisible = false;

    private void setupPasswordToggle() {
        ImageView toggle = findViewById(R.id.btn_toggle_password);
        toggle.setOnClickListener(v -> {
            passwordVisible = !passwordVisible;
            inputPassword.setTransformationMethod(passwordVisible
                    ? HideReturnsTransformationMethod.getInstance()
                    : PasswordTransformationMethod.getInstance());
            toggle.setImageResource(passwordVisible ? R.drawable.ic_eye_off : R.drawable.ic_eye);
            inputPassword.setSelection(inputPassword.getText().length());
        });
    }

    private void startGoogleSignIn() {
        // Sign out first so the account chooser always shows.
        googleClient.signOut().addOnCompleteListener(this,
                t -> googleLauncher.launch(googleClient.getSignInIntent()));
    }

    private void handleGoogleResult(Task<com.google.android.gms.auth.api.signin.GoogleSignInAccount> task) {
        try {
            String idToken = task.getResult(ApiException.class).getIdToken();
            if (idToken == null) {
                Toast.makeText(this, R.string.error_google, Toast.LENGTH_LONG).show();
                return;
            }
            setLoading(true);
            ApiClient.get(this).loginWithGoogle(new dto.GoogleLoginRequest(idToken))
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
        } catch (ApiException e) {
            Log.e("GoogleSignIn", "Sign-in failed: statusCode=" + e.getStatusCode()
                    + " (10=DEVELOPER_ERROR/cert mismatch, 7=NETWORK, 12501=CANCELLED)", e);
            Toast.makeText(this, R.string.error_google, Toast.LENGTH_LONG).show();
        }
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
        store.setPhotoUrl(body.user != null ? body.user.photoUrl : null);

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
