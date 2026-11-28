package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;

/**
 * Every v3 screen extends this: edge-to-edge dark bars and the Confess
 * screen transitions (from the theme). Ported from Confess's BaseActivity
 * without its live-theme, app-lock and session-event parts, which Kasrat
 * doesn't have yet.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeInsets.edgeToEdge(this);
    }

    /** Screens shown before sign-in (language, login, register, forgot) return false. */
    protected boolean requiresSession() {
        return true;
    }
}
