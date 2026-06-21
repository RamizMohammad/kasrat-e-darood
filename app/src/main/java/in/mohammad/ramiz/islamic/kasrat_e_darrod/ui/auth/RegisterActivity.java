package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/** Step 1 of the multi-step registration flow from the Stitch design. */
public class RegisterActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        MaterialButton cont = findViewById(R.id.btn_continue);
        cont.setOnClickListener(v -> {
            Toast.makeText(this, "Continue to step 2", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        findViewById(R.id.link_login).setOnClickListener(v -> finish());
    }
}
