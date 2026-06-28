package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.Patterns;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
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
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Email/password registration. Creates an account on the live backend
 * (POST /api/v1/auth/register), stores the returned app JWT, then enters the app.
 */
public class RegisterActivity extends AppCompatActivity {

    private EditText inputName;
    private EditText inputContact;
    private EditText inputPassword;
    private RadioGroup langGroup;
    private MaterialButton cont;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        inputName = findViewById(R.id.input_name);
        inputContact = findViewById(R.id.input_contact);
        inputPassword = findViewById(R.id.input_password);
        langGroup = findViewById(R.id.reg_lang_group);
        cont = findViewById(R.id.btn_continue);

        cont.setOnClickListener(v -> attemptRegister());
        findViewById(R.id.link_login).setOnClickListener(v -> finish());
        setupPasswordToggle();
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

    private String selectedLang() {
        int id = langGroup.getCheckedRadioButtonId();
        if (id == R.id.reg_lang_hi) return "hi";
        if (id == R.id.reg_lang_ur) return "ur";
        return "en";
    }

    private void attemptRegister() {
        String name = inputName.getText().toString().trim();
        String email = inputContact.getText().toString().trim();
        String password = inputPassword.getText().toString();

        if (TextUtils.isEmpty(name)) {
            inputName.setError(getString(R.string.error_name_required));
            inputName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            inputContact.setError(getString(R.string.error_invalid_email));
            inputContact.requestFocus();
            return;
        }
        if (password.length() < 8) {
            inputPassword.setError(getString(R.string.error_password_short));
            inputPassword.requestFocus();
            return;
        }

        String lang = selectedLang();
        // Apply the chosen language to the app immediately.
        new AppPrefs(this).setLang(lang);

        setLoading(true);
        ApiClient.get(this).register(new dto.RegisterRequest(email, password, name, lang))
                .enqueue(new Callback<dto.LoginResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.LoginResponse> call,
                                           @NonNull Response<dto.LoginResponse> response) {
                        setLoading(false);
                        if (response.isSuccessful() && response.body() != null) {
                            onAuthSuccess(response.body());
                        } else {
                            Toast.makeText(RegisterActivity.this,
                                    ApiErrors.messageFrom(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.LoginResponse> call,
                                          @NonNull Throwable t) {
                        setLoading(false);
                        Toast.makeText(RegisterActivity.this,
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
        cont.setEnabled(!loading);
        cont.setText(loading ? getString(R.string.creating_account) : getString(R.string.cont));
    }
}
