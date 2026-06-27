package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;

/** Static "about the developer" screen. */
public class DeveloperInfoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer_info);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }
}
