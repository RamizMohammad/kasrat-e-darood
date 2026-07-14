package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.KeyboardInsets;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** App settings: language, dark mode, and account deletion. */
public class SettingsActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private boolean initialising = true;

    private MaterialCardView cardEn, cardHi, cardUr;
    private ImageView checkEn, checkHi, checkUr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        KeyboardInsets.attach(this);
        prefs = new AppPrefs(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        // Language (selectable cards)
        cardEn = findViewById(R.id.card_en);
        cardHi = findViewById(R.id.card_hi);
        cardUr = findViewById(R.id.card_ur);
        checkEn = findViewById(R.id.check_en);
        checkHi = findViewById(R.id.check_hi);
        checkUr = findViewById(R.id.check_ur);

        highlightLang(prefs.lang());
        cardEn.setOnClickListener(v -> onLangCard("en"));
        cardHi.setOnClickListener(v -> onLangCard("hi"));
        cardUr.setOnClickListener(v -> onLangCard("ur"));

        // Dark mode
        MaterialSwitch dark = findViewById(R.id.switch_dark);
        dark.setChecked(prefs.darkMode());
        dark.setOnCheckedChangeListener((b, checked) -> {
            if (initialising) return;
            prefs.setDarkMode(checked);   // recreates activities via night-mode change
        });

        findViewById(R.id.row_delete_account).setOnClickListener(v ->
                startActivity(new Intent(this, DeleteAccountActivity.class)));

        initialising = false;
    }

    private void onLangCard(String tag) {
        if (initialising || tag.equals(prefs.lang())) return;
        highlightLang(tag);
        applyLanguage(tag);
    }

    private void highlightLang(String tag) {
        int selected = ContextCompat.getColor(this, R.color.primary);
        int normal = ContextCompat.getColor(this, R.color.outline_variant);
        int strokeSel = (int) (getResources().getDisplayMetrics().density * 2);
        int strokeNorm = (int) (getResources().getDisplayMetrics().density);

        boolean en = "en".equals(tag), hi = "hi".equals(tag), ur = "ur".equals(tag);
        cardEn.setStrokeColor(en ? selected : normal);
        cardEn.setStrokeWidth(en ? strokeSel : strokeNorm);
        checkEn.setVisibility(en ? View.VISIBLE : View.GONE);
        cardHi.setStrokeColor(hi ? selected : normal);
        cardHi.setStrokeWidth(hi ? strokeSel : strokeNorm);
        checkHi.setVisibility(hi ? View.VISIBLE : View.GONE);
        cardUr.setStrokeColor(ur ? selected : normal);
        cardUr.setStrokeWidth(ur ? strokeSel : strokeNorm);
        checkUr.setVisibility(ur ? View.VISIBLE : View.GONE);
    }

    private void applyLanguage(String tag) {
        prefs.setLang(tag);   // persists + applies per-app locale (recreates UI)
        // Persist to the server so emails use the same language.
        ApiClient.get(this).updateMe(new dto.UserUpdateRequest(tag))
                .enqueue(new Callback<dto.UserDto>() {
                    @Override public void onResponse(@NonNull Call<dto.UserDto> c,
                                                     @NonNull Response<dto.UserDto> r) {}
                    @Override public void onFailure(@NonNull Call<dto.UserDto> c,
                                                    @NonNull Throwable t) {}
                });
        Toast.makeText(this, R.string.language_updated, Toast.LENGTH_SHORT).show();
    }
}
