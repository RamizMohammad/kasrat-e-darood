package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;

/** Branded splash; routes to the app or login after a short delay. */
@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {
    private static final long SPLASH_MS = 1400L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            boolean loggedIn = new TokenStore(this).isLoggedIn();
            Intent intent = loggedIn
                    ? new Intent(this, MainActivity.class)
                    : new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
        }, SPLASH_MS);
    }
}
