package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth;

import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.PasswordStrength;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.SimpleTextWatcher;

/** Live password-strength bar under a password field (register + reset). */
final class StrengthMeter {

    private StrengthMeter() { }

    static void attach(@NonNull EditText input, @NonNull View row,
                       @NonNull LinearProgressIndicator bar, @NonNull TextView label) {
        input.addTextChangedListener(new SimpleTextWatcher(s -> {
            String pw = s == null ? "" : s.toString();
            if (pw.isEmpty()) {
                row.setVisibility(View.INVISIBLE);
                return;
            }
            row.setVisibility(View.VISIBLE);
            int score = PasswordStrength.longEnough(pw) ? PasswordStrength.score(pw) : 0;
            int text, color;
            switch (score) {
                case 3: text = R.string.pw_strength_3; color = R.color.sage; break;
                case 2: text = R.string.pw_strength_2; color = R.color.amber; break;
                case 1: text = R.string.pw_strength_1; color = R.color.brick; break;
                default: text = R.string.pw_strength_0; color = R.color.brick; break;
            }
            int c = ContextCompat.getColor(input.getContext(), color);
            bar.setProgressCompat(Math.max(score, 1), true);
            bar.setIndicatorColor(c);
            bar.setTrackColor(ContextCompat.getColor(input.getContext(), R.color.hairline));
            label.setText(text);
            label.setTextColor(c);
        }));
    }
}
