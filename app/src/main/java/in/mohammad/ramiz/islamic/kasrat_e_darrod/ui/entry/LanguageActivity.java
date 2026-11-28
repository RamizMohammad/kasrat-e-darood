package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.entry;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.ActivityLanguageBinding;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.nav.Nav;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;

/**
 * First run only: pick English, Hindi or Urdu. The choice is applied straight
 * away (the screen redraws in that language) and Continue moves on to sign-in.
 */
public class LanguageActivity extends BaseActivity {

    private static final String STATE_PICK = "pick";

    private ActivityLanguageBinding b;
    @Nullable private String picked;

    @Override
    protected boolean requiresSession() {
        return false;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityLanguageBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        EdgeInsets.padSystemBars(b.root, false);

        AppPrefs prefs = new AppPrefs(this);
        // After the locale switch the activity is recreated: savedInstanceState keeps the pick.
        picked = savedInstanceState != null ? savedInstanceState.getString(STATE_PICK) : null;

        b.langEn.setOnClickListener(v -> pick("en"));
        b.langHi.setOnClickListener(v -> pick("hi"));
        b.langUr.setOnClickListener(v -> pick("ur"));
        b.btnContinue.setOnClickListener(v -> {
            if (picked == null) return;
            Haptics.click(this);
            prefs.markLanguageChosen();
            Nav.next(this);
            finish();
        });
        render();
    }

    private void pick(String tag) {
        Haptics.tick(this);
        boolean changed = !tag.equals(picked);
        picked = tag;
        render();
        if (changed) {
            // Recreates every activity in the new language; savedInstanceState keeps the pick.
            new AppPrefs(this).setLang(tag);
        }
    }

    private void render() {
        select(b.langEn, b.langEnCheck, "en".equals(picked));
        select(b.langHi, b.langHiCheck, "hi".equals(picked));
        select(b.langUr, b.langUrCheck, "ur".equals(picked));
        b.btnContinue.setEnabled(picked != null);
    }

    private static void select(View card, View check, boolean on) {
        card.setSelected(on);
        check.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(STATE_PICK, picked);
    }
}
