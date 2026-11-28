package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.tasks.Task;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.ActivityLoginBinding;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.nav.Nav;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * P1 · Sign in with email + password or Google (POST /auth/login, /auth/google).
 * Errors show inline: field errors under the inputs, server errors in the plate.
 */
public class LoginActivity extends BaseActivity {

    private ActivityLoginBinding b;
    private GoogleSignInClient googleClient;
    private boolean busy;

    private final ActivityResultLauncher<Intent> googleLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> onGoogleResult(GoogleSignIn.getSignedInAccountFromIntent(result.getData())));

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Passwords, codes, account deletion: no screenshots, no tapjacking.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.security.ScreenGuard.protect(this);
        b = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.column, true);
        EdgeInsets.padTop(b.progress);
        if (savedInstanceState == null) Motion.enterStack(b.column);

        googleClient = GoogleSignIn.getClient(this, new GoogleSignInOptions.Builder(
                GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build());

        b.btnSignIn.setOnClickListener(v -> attemptLogin());
        b.inputPassword.setOnEditorActionListener((v, actionId, e) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { attemptLogin(); return true; }
            return false;
        });
        b.btnGoogle.setOnClickListener(v -> {
            if (busy) return;
            Haptics.click(this);
            // Sign out first so the account chooser always shows.
            googleClient.signOut().addOnCompleteListener(this,
                    t -> googleLauncher.launch(googleClient.getSignInIntent()));
        });
        b.linkRegister.setOnClickListener(v -> Nav.register(this, text(b.inputEmail)));
        b.linkForgot.setOnClickListener(v -> Nav.forgotPassword(this, text(b.inputEmail)));
    }

    private void attemptLogin() {
        if (busy) return;
        clearErrors();
        String email = text(b.inputEmail);
        String password = b.inputPassword.getText() == null ? "" : b.inputPassword.getText().toString();
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            b.inputEmailLayout.setError(getString(R.string.err_email));
            b.inputEmail.requestFocus();
            Haptics.reject(this);
            return;
        }
        if (password.isEmpty()) {
            b.inputPasswordLayout.setError(getString(R.string.err_password_required));
            b.inputPassword.requestFocus();
            Haptics.reject(this);
            return;
        }
        setBusy(true);
        ApiClient.get(this).login(new dto.EmailLoginRequest(email, password)).enqueue(callback());
    }

    private void onGoogleResult(Task<GoogleSignInAccount> task) {
        try {
            String idToken = task.getResult(ApiException.class).getIdToken();
            if (idToken == null) {
                AuthSession.showError(b.error, getString(R.string.err_google));
                return;
            }
            clearErrors();
            setBusy(true);
            ApiClient.get(this).loginWithGoogle(new dto.GoogleLoginRequest(idToken)).enqueue(callback());
        } catch (ApiException e) {
            if (e.getStatusCode() == CommonStatusCodes.CANCELED || e.getStatusCode() == 12501) return;
            Log.w("GoogleSignIn", "statusCode=" + e.getStatusCode()
                    + " (10=DEVELOPER_ERROR/SHA-1 mismatch, 7=NETWORK)");
            AuthSession.showError(b.error, getString(R.string.err_google));
        }
    }

    private Callback<dto.LoginResponse> callback() {
        return new Callback<dto.LoginResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.LoginResponse> call,
                                   @NonNull Response<dto.LoginResponse> response) {
                setBusy(false);
                if (response.isSuccessful() && response.body() != null) {
                    Haptics.click(LoginActivity.this);
                    AuthSession.complete(LoginActivity.this, response.body());
                } else {
                    Haptics.reject(LoginActivity.this);
                    ApiErrors.Failure f = ApiErrors.of(response);
                    AuthSession.showError(b.error, getString(AuthSession.messageFor(f, R.string.err_generic)));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.LoginResponse> call, @NonNull Throwable t) {
                setBusy(false);
                AuthSession.showError(b.error, getString(R.string.err_network));
            }
        };
    }

    private void setBusy(boolean on) {
        busy = on;
        b.progress.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        b.btnSignIn.setEnabled(!on);
        b.btnGoogle.setEnabled(!on);
        b.btnSignIn.setText(on ? R.string.login_signing_in : R.string.login_sign_in);
    }

    private void clearErrors() {
        b.inputEmailLayout.setError(null);
        b.inputPasswordLayout.setError(null);
        AuthSession.showError(b.error, null);
    }

    private static String text(android.widget.EditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }
}
