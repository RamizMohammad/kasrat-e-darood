package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/**
 * Email/Google sign-in screen. Wire {@code btn_sign_in} / {@code btn_google} to
 * Firebase Auth, then exchange the Firebase ID token via
 * ApiService.loginWithFirebase(...) and store the returned app JWT.
 */
public class LoginActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        MaterialButton signIn = findViewById(R.id.btn_sign_in);
        MaterialButton google = findViewById(R.id.btn_google);

        signIn.setOnClickListener(v -> enterApp());
        google.setOnClickListener(v -> {
            Toast.makeText(this, "Connect Google / Firebase sign-in", Toast.LENGTH_SHORT).show();
            enterApp();
        });

        findViewById(R.id.link_register).setOnClickListener(v ->
                startActivity(new Intent(this, RegisterActivity.class)));
    }

    private void enterApp() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
