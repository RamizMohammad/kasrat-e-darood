package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.UUID;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.TasbihRingView;
import androidx.core.content.ContextCompat;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.LogService;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.RecitationStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.CountRollTextView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.CounterRingView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.TickView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;

/**
 * Tasbih (Stitch tasbih_counter_teal, and tasbih_counter_night_teal when Night is picked
 * from the display button). The big number is the count within the current lap; "of 100 ·
 * lap 2" and the gold dots show the laps; "Save 167 darood" logs the whole count.
 *
 * Tasbih counter (P2 screen 12).
 *
 * Every tap is saved straight away (per recitation), so the count survives the
 * phone locking, rotation and the app being closed. "Log N" sends the count
 * with a client_uuid; pressing "Try again" without counting more re-sends the
 * same one, so a retry after a dropped connection can't log it twice. The
 * screen stays on while open.
 */
public class TasbihActivity extends BaseActivity {

    private static final String EXTRA_ID = "recitation_id";
    private static final String PREFS = "kasrat_tasbih";
    private static final int MAX_TARGET = 99_999;

    @NonNull
    public static Intent intent(@NonNull Context ctx, @NonNull String recitationId) {
        return new Intent(ctx, TasbihActivity.class).putExtra(EXTRA_ID, recitationId);
    }

    private String id;
    private SharedPreferences prefs;
    private TasbihRingView ring;
    private boolean night;
    private CountRollTextView countText;
    private TextView of, error;
    private View tapArea, done;
    private MaterialButton log, undo, reset;
    private View targets;
    private int count, target;
    private String uuid;
    private boolean sending;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tasbih);
        if (new AppPrefs(this).keepScreenOn()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        EdgeInsets.padSystemBars(findViewById(R.id.root), false);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        id = getIntent().getStringExtra(EXTRA_ID);
        if (id == null) {
            finish();
            return;
        }
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        count = prefs.getInt(id + ":count", 0);
        target = prefs.getInt(id + ":target", new AppPrefs(this).tasbihTarget());
        uuid = prefs.getString(id + ":uuid", null);
        if (uuid == null) newUuid();

        ring = findViewById(R.id.tasbih_ring);
        countText = findViewById(R.id.tasbih_count);
        of = findViewById(R.id.tasbih_of);
        error = findViewById(R.id.tasbih_error);
        done = findViewById(R.id.tasbih_done);
        tapArea = findViewById(R.id.tasbih_tap);
        log = findViewById(R.id.tasbih_log);
        undo = findViewById(R.id.tasbih_undo);
        reset = findViewById(R.id.tasbih_reset);
        targets = findViewById(R.id.tasbih_targets);
        ((TextView) findViewById(R.id.target_1000)).setText(Numbers.format(1000));
        night = prefs.getBoolean("night_view", false);
        findViewById(R.id.tasbih_settings).setOnClickListener(v -> pickView());

        bindRecitation();
        tapArea.setOnClickListener(v -> tap());
        undo.setOnClickListener(v -> undo());
        reset.setOnClickListener(v -> resetLap());
        log.setOnClickListener(v -> send());
        bindTargetChips();
        applyView();
        render(false);
    }

    private void bindRecitation() {
        String lang = new AppPrefs(this).lang();
        RecitationStore store = RecitationStore.get(this);
        dto.RecitationDto r = store.byId(id);
        if (r != null) {
            show(r, lang);
            return;
        }
        store.load(new RecitationStore.Callback() {
            @Override public void onList(@NonNull List<dto.RecitationDto> list, boolean fromCache) {
                dto.RecitationDto found = store.byId(id);
                if (found != null && !isFinishing()) show(found, lang);
            }

            @Override public void onError(boolean offline) { }
        });
    }

    private void show(@NonNull dto.RecitationDto r, @NonNull String lang) {
        ((TextView) findViewById(R.id.tasbih_name)).setText(RecitationStore.title(r, lang));
        ((TextView) findViewById(R.id.tasbih_arabic)).setText(r.arabicName);
    }

    // ── Counting ────────────────────────────────────────────────────────────

    private void tap() {
        if (sending) return;
        count++;
        newUuid();          // a different count is a different log
        save();
        done.setVisibility(View.GONE);
        error.setVisibility(View.GONE);
        boolean atTarget = count % target == 0;
        AppPrefs app = new AppPrefs(this);
        if (app.haptics()) tapArea.performHapticFeedback(atTarget
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.CLOCK_TICK);
        if (app.sound()) tapArea.playSoundEffect(android.view.SoundEffectConstants.CLICK);
        render(true);
    }

    private void undo() {
        if (count <= 0 || sending) return;
        count--;
        newUuid();
        save();
        Haptics.tick(this);
        render(true);
    }

    /** Drops the taps of the lap in progress; finished laps stay. */
    private void resetLap() {
        int inLap = count % target;
        if (inLap == 0 || sending) return;
        count -= inLap;
        newUuid();
        save();
        Haptics.tick(this);
        done.setVisibility(View.GONE);
        render(true);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Volume keys count too, so you can keep the phone in your pocket.
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.getRepeatCount() == 0) tap();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void render(boolean animate) {
        // The number is the count within the lap; a finished lap shows the full target.
        int inLap = count % target;
        boolean lapEnd = count > 0 && inLap == 0;
        int shownCount = lapEnd ? target : inLap;
        int lap = lapEnd ? count / target : count / target + 1;
        int lapsDone = count / target;
        ring.setProgress(lapEnd ? 1f : (float) inLap / target, lapsDone, animate);
        countText.setCount(shownCount, animate && countText.hasCount());
        of.setText(getString(R.string.tasbih_of_lap, Numbers.format(target), Math.max(1, lap)));
        ring.setContentDescription(lapsDone > 0 ? getString(R.string.tasbih_laps_cd, lapsDone) : null);
        tapArea.setContentDescription(getString(R.string.tasbih_tap_cd, count, target));
        undo.setEnabled(count > 0 && !sending);
        reset.setEnabled(count % target != 0 && !sending);
        log.setEnabled(count > 0 && !sending);
        if (!sending) log.setText(count > 0
                ? getString(R.string.tasbih_save_n, Numbers.format(count))
                : getString(R.string.tasbih_save_empty));
    }

    private void save() {
        prefs.edit().putInt(id + ":count", count).putInt(id + ":target", target)
                .putString(id + ":uuid", uuid).apply();
    }

    private void newUuid() {
        uuid = UUID.randomUUID().toString();
    }

    // ── Target ──────────────────────────────────────────────────────────────

    private void bindTargetChips() {
        findViewById(R.id.target_33).setOnClickListener(v -> pickTarget(33));
        findViewById(R.id.target_100).setOnClickListener(v -> pickTarget(100));
        findViewById(R.id.target_1000).setOnClickListener(v -> pickTarget(1000));
        // Custom opens a number dialog every tap, even when already selected.
        findViewById(R.id.target_custom).setOnClickListener(v -> askCustom());
        markTargets();
    }

    private void pickTarget(int t) {
        Haptics.tick(this);
        setTarget(t);
    }

    private void setTarget(int t) {
        target = Math.max(1, Math.min(MAX_TARGET, t));
        ((TextView) findViewById(R.id.target_custom)).setText(
                isPreset(target) ? getString(R.string.tasbih_custom) : Numbers.format(target));
        save();
        markTargets();
        render(false);
    }

    /** Stitch text tabs: the current target is ink (ivory at night) with a teal (gold) underline. */
    private void markTargets() {
        int on = ContextCompat.getColor(this, night ? R.color.k_warm_ivory : R.color.text_primary);
        int off = ContextCompat.getColor(this, night ? R.color.k_night_muted : R.color.text_secondary);
        int current = targetChip(target);
        for (int id : new int[]{R.id.target_33, R.id.target_100, R.id.target_1000, R.id.target_custom}) {
            TextView t = findViewById(id);
            boolean sel = id == current;
            t.setSelected(sel);
            t.setTextColor(sel ? on : off);
            t.setBackgroundResource(sel ? (night ? R.drawable.bg_k_underline_gold : R.drawable.bg_k_underline) : 0);
        }
    }

    private static boolean isPreset(int t) { return t == 33 || t == 100 || t == 1000; }

    private static int targetChip(int t) {
        return t == 33 ? R.id.target_33 : t == 100 ? R.id.target_100
                : t == 1000 ? R.id.target_1000 : R.id.target_custom;
    }

    // ── Day / Night (Stitch tasbih_counter_night_teal) ──────────────────────

    private void pickView() {
        Haptics.tick(this);
        in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.ChoiceSheet.with(this)
                .title(R.string.tasbih_view)
                .option(R.drawable.ic_ph_sun, getString(R.string.tasbih_day), getString(R.string.tasbih_day_sub),
                        !night, () -> setNight(false))
                .option(R.drawable.ic_ph_moon, getString(R.string.tasbih_night), getString(R.string.tasbih_night_sub),
                        night, () -> setNight(true))
                .show();
    }

    private void setNight(boolean on) {
        night = on;
        prefs.edit().putBoolean("night_view", on).apply();
        applyView();
    }

    /** Recolours this screen only: night-ink canvas, night-surface buttons, ivory text, gold accent. */
    private void applyView() {
        int bg = ContextCompat.getColor(this, night ? R.color.k_night_ink : R.color.k_warm_ivory);
        int text = ContextCompat.getColor(this, night ? R.color.k_warm_ivory : R.color.k_deep_ink);
        int muted = ContextCompat.getColor(this, night ? R.color.k_night_muted : R.color.k_deep_ink_70);
        int surface = ContextCompat.getColor(this, night ? R.color.k_night_surface : R.color.k_pure_white);
        int line = ContextCompat.getColor(this, night ? R.color.k_night_hairline : R.color.k_hairline);
        findViewById(R.id.root).setBackgroundColor(bg);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        androidx.core.view.WindowInsetsControllerCompat bars =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        boolean lightBars = !night && !AppPrefs.isDark(this);
        bars.setAppearanceLightStatusBars(lightBars);
        bars.setAppearanceLightNavigationBars(lightBars);

        ((TextView) findViewById(R.id.tasbih_name)).setTextColor(text);
        countText.setTextColor(text);
        of.setTextColor(muted);
        ((TextView) findViewById(R.id.tasbih_hint)).setTextColor(muted);
        ((TextView) findViewById(R.id.tasbih_undo_label)).setTextColor(muted);
        ((TextView) findViewById(R.id.tasbih_reset_label)).setTextColor(muted);
        for (int id : new int[]{R.id.btn_back, R.id.tasbih_settings, R.id.tasbih_undo, R.id.tasbih_reset}) {
            MaterialButton b = findViewById(id);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(surface));
            b.setStrokeColor(android.content.res.ColorStateList.valueOf(line));
            b.setIconTint(android.content.res.ColorStateList.valueOf(text));
            b.setElevation(night ? 0 : 3 * getResources().getDisplayMetrics().density);
        }
        // Save pill: deep teal / white by day, gold / night-ink at night.
        int pill = ContextCompat.getColor(this, night ? R.color.k_gold : R.color.k_deep_teal);
        int onPill = ContextCompat.getColor(this, night ? R.color.k_night_ink : R.color.k_pure_white);
        log.setBackgroundTintList(new android.content.res.ColorStateList(
                new int[][]{{-android.R.attr.state_enabled}, {}},
                new int[]{androidx.core.graphics.ColorUtils.setAlphaComponent(pill, 0x59), pill}));
        log.setTextColor(onPill);
        log.setOutlineSpotShadowColor(pill);
        ring.setNight(night);
        markTargets();
    }

    private void askCustom() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_number, null, false);
        TextInputLayout layout = v.findViewById(R.id.number_layout);
        TextInputEditText input = v.findViewById(R.id.number_input);
        if (!isPreset(target)) input.setText(String.valueOf(target));
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.tasbih_custom_title)
                .setView(v)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            int n;
            try {
                n = Integer.parseInt(String.valueOf(input.getText()).trim());
            } catch (NumberFormatException e) {
                n = 0;
            }
            if (n < 1 || n > MAX_TARGET) {
                layout.setError(getString(R.string.tasbih_custom_error, Numbers.format(MAX_TARGET)));
                return;
            }
            setTarget(n);
            d.dismiss();
        }));
        d.setOnDismissListener(x -> markTargets());
        d.show();
    }

    // ── Logging ─────────────────────────────────────────────────────────────

    private void send() {
        if (count <= 0 || sending) return;
        sending = true;
        int sent = count;
        log.setText(R.string.cart_logging);
        render(false);
        LogService.logOne(this, id, sent, uuid, new LogService.Result() {
            @Override
            public void onLogged(@NonNull dto.Totals totals) {
                sending = false;
                count = 0;                                // taps are blocked while sending
                newUuid();
                save();
                if (isFinishing()) return;
                Haptics.click(TasbihActivity.this);
                done.setVisibility(View.VISIBLE);
                ((TextView) findViewById(R.id.tasbih_done_text)).setText(
                        getString(R.string.tasbih_logged, Numbers.format(sent)));
                ((TickView) findViewById(R.id.tasbih_tick)).play();
                render(true);
            }

            @Override
            public void onFailed(@NonNull String message, boolean offline) {
                sending = false;
                if (isFinishing()) return;
                Haptics.reject(TasbihActivity.this);
                error.setText(getString(R.string.tasbih_failed, message));
                error.setVisibility(View.VISIBLE);
                render(false);
            }
        });
    }
}
