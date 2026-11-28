package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.DialogKasratBinding;

/**
 * Confess's own centred popup, instead of the system's alert dialogs: an icon on an amber disc,
 * a serif title, a line about it, an optional password field, one amber button and a quiet
 * "Cancel". It eases in; a wrong password shakes it.
 *
 *   KasratDialog.with(ctx).icon(R.drawable.ic_lock).title(R.string.x).body(R.string.y)
 *           .password(R.string.hint)
 *           .primary(R.string.go, d -> { d.busy(true); … d.error("Wrong password"); … d.dismiss(); })
 *           .show();
 *
 * The primary action doesn't close it by itself (it may need to check something first);
 * the secondary one does, then runs its callback.
 */
public final class KasratDialog {

    public interface Action {
        void run(@NonNull KasratDialog d);
    }

    private final Context ctx;
    @DrawableRes private int icon;
    @Nullable private CharSequence title, body, primaryLabel, secondaryLabel;
    @StringRes private int passwordHint;
    private boolean danger, cancelable = true, plainField;
    @Nullable private Action primary;
    @Nullable private Runnable secondary, onDismiss;

    @Nullable private Dialog dialog;
    @Nullable private DialogKasratBinding v;

    private KasratDialog(@NonNull Context ctx) {
        this.ctx = ctx;
    }

    @NonNull
    public static KasratDialog with(@NonNull Context ctx) {
        return new KasratDialog(ctx);
    }

    @NonNull public KasratDialog icon(@DrawableRes int res) { icon = res; return this; }
    @NonNull public KasratDialog title(@StringRes int res) { title = ctx.getString(res); return this; }
    @NonNull public KasratDialog body(@StringRes int res) { body = ctx.getString(res); return this; }
    @NonNull public KasratDialog body(@Nullable CharSequence text) { body = text; return this; }

    /** Adds a password field (focused, keyboard up); read it with {@link #text()}. */
    @NonNull public KasratDialog password(@StringRes int hint) { passwordHint = hint; return this; }

    /** A plain text field instead of a password one (e.g. "type the group's name"). */
    @NonNull public KasratDialog textField(@StringRes int hint) { passwordHint = hint; plainField = true; return this; }

    @NonNull
    public KasratDialog primary(@StringRes int label, @NonNull Action action) {
        primaryLabel = ctx.getString(label);
        primary = action;
        return this;
    }

    /** The amber button turns brick red (turning something off, signing out). */
    @NonNull public KasratDialog danger() { danger = true; return this; }

    @NonNull
    public KasratDialog secondary(@StringRes int label, @Nullable Runnable action) {
        secondaryLabel = ctx.getString(label);
        secondary = action;
        return this;
    }

    /** False: only the buttons close it (no back / tap outside). */
    @NonNull public KasratDialog cancelable(boolean c) { cancelable = c; return this; }

    @NonNull public KasratDialog onDismiss(@Nullable Runnable r) { onDismiss = r; return this; }

    @NonNull
    public KasratDialog show() {
        DialogKasratBinding b = DialogKasratBinding.inflate(LayoutInflater.from(ctx));
        v = b;
        Dialog d = new Dialog(ctx);
        dialog = d;
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        d.setContentView(b.getRoot());
        d.setCancelable(cancelable);
        d.setCanceledOnTouchOutside(cancelable);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int side = Math.round(24 * ctx.getResources().getDisplayMetrics().density);
            int width = Math.min(ctx.getResources().getDisplayMetrics().widthPixels - 2 * side,
                    Math.round(400 * ctx.getResources().getDisplayMetrics().density));
            w.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.62f);
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | (passwordHint != 0 ? WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE : 0));
        }

        if (icon != 0) b.icon.setImageResource(icon); else b.iconDisc.setVisibility(View.GONE);
        b.title.setText(title);
        if (body != null) {
            b.body.setVisibility(View.VISIBLE);
            b.body.setText(body);
        }
        if (passwordHint != 0) {
            b.fieldLayout.setVisibility(View.VISIBLE);
            b.fieldLayout.setHint(passwordHint);
            if (plainField) {
                b.fieldLayout.setEndIconMode(com.google.android.material.textfield.TextInputLayout.END_ICON_NONE);
                b.field.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                        | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                b.field.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
            }
            b.field.addTextChangedListener(new android.text.TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int c, int e) { }
                @Override public void onTextChanged(CharSequence s, int a, int c, int e) { b.fieldLayout.setError(null); }
                @Override public void afterTextChanged(android.text.Editable s) { }
            });
            b.field.setOnEditorActionListener((tv, id, ev) -> {
                if (id == EditorInfo.IME_ACTION_DONE) { b.primary.performClick(); return true; }
                return false;
            });
        }
        b.primary.setText(primaryLabel);
        if (danger) {
            int red = ContextCompat.getColor(ctx, R.color.brick);
            b.primary.setBackgroundTintList(ColorStateList.valueOf(red));
            b.primary.setOutlineSpotShadowColor(red);          // Hero's glow, in red
        }
        b.primary.setOnClickListener(x -> {
            if (passwordHint != 0 && text().isEmpty()) {
                shake();
                return;
            }
            Haptics.click(ctx);
            if (primary != null) primary.run(this); else dismiss();
        });
        if (secondaryLabel != null) b.secondary.setText(secondaryLabel);
        b.secondary.setOnClickListener(x -> {
            dismiss();
            if (secondary != null) secondary.run();
        });
        if (onDismiss != null) d.setOnDismissListener(x -> onDismiss.run());

        d.show();
        // Ease in: a little grow and fade.
        if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
            b.card.setAlpha(0f);
            b.card.setScaleX(0.92f);
            b.card.setScaleY(0.92f);
            b.card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(220)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.1f)).start();
        }
        if (passwordHint != 0) {
            b.field.requestFocus();
            b.field.postDelayed(() -> {
                InputMethodManager imm = (InputMethodManager) ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(b.field, InputMethodManager.SHOW_IMPLICIT);
            }, 150);
        }
        return this;
    }

    /** What's in the password field. */
    @NonNull
    public String text() {
        return v == null || v.field.getText() == null ? "" : v.field.getText().toString();
    }

    /** Working on it: buttons and field off. */
    public void busy(boolean on) {
        if (v == null) return;
        v.primary.setEnabled(!on);
        v.secondary.setEnabled(!on);
        v.field.setEnabled(!on);
    }

    /** Show what went wrong (under the field when there is one) and shake. */
    public void error(@NonNull CharSequence message) {
        if (v == null) return;
        busy(false);
        if (v.fieldLayout.getVisibility() == View.VISIBLE) {
            v.fieldLayout.setError(message);
            v.field.selectAll();
        } else {
            v.body.setVisibility(View.VISIBLE);
            v.body.setText(message);
        }
        shake();
    }

    public void dismiss() {
        if (dialog != null && dialog.isShowing()) dialog.dismiss();
    }

    private void shake() {
        if (v == null) return;
        Haptics.reject(ctx);
        View c = v.card;
        c.animate().cancel();
        c.animate().translationX(-18f).setDuration(50).withEndAction(() ->
                c.animate().translationX(14f).setDuration(50).withEndAction(() ->
                        c.animate().translationX(-8f).setDuration(50).withEndAction(() ->
                                c.animate().translationX(0f).setDuration(50).start()).start()).start()).start();
    }
}
