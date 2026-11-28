package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.google.gson.JsonObject;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.BuildConfig;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.avatar.AvatarStudioActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.ChoiceSheet;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.SegmentedToggle;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile.DeveloperInfoActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile.SignOut;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Phase 7c · Settings (Stitch settings_teal).
 *
 * Account (avatar, name, email → change avatar / delete account) · Recitation (daily intention,
 * default tasbih target, sound, haptics, keep screen on) · Reminders (daily time, Friday,
 * prayer-time nudges) · Appearance (theme, language) · Prayer times · Notifications · Privacy
 * (show me on global ranks) · About (version, rate, about) · Sign out.
 *
 * Server-backed choices (language, prayer, privacy, push types, the daily reminder) PATCH
 * /users/me with just that field, show at once and are put back if saving fails. The rest
 * (intention, tasbih, sound, haptics, screen, Friday, nudges) live on this phone (AppPrefs).
 */
public class SettingsActivity extends BaseActivity {

    /** AlAdhan methods offered (id → name); the common ones for South Asia and the Gulf. */
    private static final int[] METHOD_IDS = {1, 2, 3, 4, 5, 8, 15, 16};
    private static final int[] TASBIH_TARGETS = {33, 100, 1000};
    private static final String[] LANGS = {"en", "hi", "ur"};

    private AppPrefs app;
    private dto.Preferences prefs = new dto.Preferences();
    private boolean loaded;
    private int saving;

    private final ActivityResultLauncher<Intent> avatarStudio = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                String spec = result.getData().getStringExtra(AvatarStudioActivity.EXTRA_SPEC);
                if (spec != null) saveAvatar(spec);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        EdgeInsets.padSystemBars(findViewById(R.id.root), false);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.settings_retry).setOnClickListener(v -> load());
        findViewById(R.id.settings_signout).setOnClickListener(v -> SignOut.confirm(this));
        app = new AppPrefs(this);
        // Local copies, so the screen is useful before (or without) the server.
        prefs.prayerMethod = app.prayerMethod();
        prefs.prayerSchool = app.prayerSchool();
        render();
        load();
        if (savedInstanceState == null) Motion.enterStack(findViewById(R.id.settings_column));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (app != null) bindAccount();
    }

    private void load() {
        findViewById(R.id.settings_error).setVisibility(View.GONE);
        busy(+1);
        ApiClient.get(this).me().enqueue(new Callback<dto.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.UserDto> call, @NonNull Response<dto.UserDto> r) {
                busy(-1);
                if (isFinishing()) return;
                if (r.isSuccessful() && r.body() != null && r.body().preferences != null) {
                    prefs = r.body().preferences;
                    if (prefs.reminders == null) prefs.reminders = new dto.Reminders();
                    if (prefs.notify == null) prefs.notify = new dto.Notify();
                    app.setPrayer(prefs.prayerMethod, prefs.prayerSchool);
                    loaded = true;
                    render();
                } else {
                    findViewById(R.id.settings_error).setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) {
                busy(-1);
                if (!isFinishing()) findViewById(R.id.settings_error).setVisibility(View.VISIBLE);
            }
        });
    }

    // ── Rows ────────────────────────────────────────────────────────────────

    private void render() {
        bindAccount();

        LinearLayout rec = clear(R.id.settings_recitation);
        valueRow(rec, R.string.set_k_intention, Numbers.format(app.dailyIntention()), v -> askIntention());
        valueRow(rec, R.string.set_k_tasbih_target, Numbers.format(app.tasbihTarget()), v -> pickTasbihTarget());
        switchRow(rec, R.string.set_k_sound, 0, app.sound(), on -> app.setSound(on));
        switchRow(rec, R.string.set_k_haptics, 0, app.haptics(), on -> app.setHaptics(on));
        switchRow(rec, R.string.set_k_keep_on, 0, app.keepScreenOn(), on -> app.setKeepScreenOn(on));

        LinearLayout rem = clear(R.id.settings_reminders);
        dto.Reminders r = prefs.reminders != null ? prefs.reminders : new dto.Reminders();
        valueRow(rem, R.string.set_k_reminder_time, r.enabled ? clock(r.time) : getString(R.string.set_k_off),
                v -> pickReminder(r)).setEnabled(loaded);
        switchRow(rem, R.string.set_k_friday, 0, app.fridayReminder(), on -> app.setFridayReminder(on));
        switchRow(rem, R.string.set_k_nudges, 0, app.prayerNudges(), on -> app.setPrayerNudges(on));

        LinearLayout look = clear(R.id.settings_appearance);
        segmentRow(look, R.string.set_k_theme, new int[]{R.string.set_k_light, R.string.set_k_dark, R.string.set_k_system},
                app.theme(), null, null, i -> {
                    Haptics.tick(this);
                    app.setTheme(i);               // recreates this screen in the new theme
                });
        String lang = app.lang();
        int li = 0;
        for (int i = 0; i < LANGS.length; i++) if (LANGS[i].equals(lang)) li = i;
        segmentRow(look, R.string.set_k_language, new int[]{R.string.lang_en, R.string.lang_hi, R.string.lang_ur},
                li, null, null, i -> setLanguage(LANGS[i]));

        LinearLayout prayer = clear(R.id.settings_prayer);
        valueRow(prayer, R.string.settings_prayer_method, methodName(prefs.prayerMethod), v -> pickMethod());
        valueRow(prayer, R.string.settings_prayer_asr,
                getString(prefs.prayerSchool == 1 ? R.string.settings_asr_hanafi : R.string.settings_asr_shafi),
                v -> pickSchool());

        LinearLayout notify = clear(R.id.settings_notify);
        dto.Notify n = prefs.notify != null ? prefs.notify : new dto.Notify();
        switchRow(notify, R.string.settings_notify_groups, R.string.settings_notify_groups_sub, n.groups,
                on -> save(nested("notify", "groups", on), () -> n.groups = on, () -> n.groups = !on)).setEnabled(loaded);
        switchRow(notify, R.string.settings_notify_streak, R.string.settings_notify_streak_sub, n.streak,
                on -> save(nested("notify", "streak", on), () -> n.streak = on, () -> n.streak = !on)).setEnabled(loaded);
        switchRow(notify, R.string.settings_notify_system, R.string.settings_notify_system_sub, n.system,
                on -> save(nested("notify", "system", on), () -> n.system = on, () -> n.system = !on)).setEnabled(loaded);
        switchRow(notify, R.string.settings_notify_activity, R.string.settings_notify_activity_sub, n.activity,
                on -> save(nested("notify", "activity", on), () -> n.activity = on, () -> n.activity = !on)).setEnabled(loaded);

        LinearLayout privacy = clear(R.id.settings_privacy);
        // Stitch asks it the other way round: "Show me" = not hidden.
        switchRow(privacy, R.string.set_k_show_me, R.string.set_k_show_me_sub, !prefs.hideOnBoards,
                on -> save(field("hide_on_boards", !on), () -> prefs.hideOnBoards = !on,
                        () -> prefs.hideOnBoards = on)).setEnabled(loaded);

        LinearLayout about = clear(R.id.settings_about);
        View version = valueRow(about, R.string.set_k_version, BuildConfig.VERSION_NAME, null);
        version.setClickable(false);
        valueRow(about, R.string.set_k_rate, null, v -> rate());
        valueRow(about, R.string.me_menu_about, null,
                v -> startActivity(new Intent(this, DeveloperInfoActivity.class)));

        for (int id : new int[]{R.id.settings_reminders, R.id.settings_notify, R.id.settings_privacy}) {
            findViewById(id).setAlpha(loaded || id == R.id.settings_reminders ? 1f : 0.5f);
        }
    }

    private void bindAccount() {
        LinearLayout account = clear(R.id.settings_account);
        View row = LayoutInflater.from(this).inflate(R.layout.item_k_account_row, account, false);
        TokenStore store = new TokenStore(this);
        String name = store.userName();
        ((TextView) row.findViewById(R.id.acc_name)).setText(TextUtils.isEmpty(name) ? getString(R.string.home_friend) : name);
        TextView email = row.findViewById(R.id.acc_email);
        email.setText(store.userEmail());
        email.setVisibility(TextUtils.isEmpty(store.userEmail()) ? View.GONE : View.VISIBLE);
        ((AuraAvatarView) row.findViewById(R.id.acc_avatar))
                .setAvatar(store.avatar() != null ? store.avatar() : store.photoUrl());
        row.setOnClickListener(v -> ChoiceSheet.with(this).title(R.string.set_k_account)
                .option(R.drawable.ic_ph_user, getString(R.string.me_menu_avatar), null, false, this::openAvatarStudio)
                .danger(R.drawable.ic_ph_trash, getString(R.string.settings_delete_account), null,
                        () -> startActivity(new Intent(this, DeleteAccountActivity.class)))
                .show());
        account.addView(row);
    }

    @NonNull
    private LinearLayout clear(int id) {
        LinearLayout l = findViewById(id);
        l.removeAllViews();
        return l;
    }

    @NonNull
    private View valueRow(@NonNull LinearLayout parent, @StringRes int label, @Nullable String value,
                          @Nullable View.OnClickListener click) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_k_setting_row, parent, false);
        ((TextView) row.findViewById(R.id.row_label)).setText(label);
        TextView v = row.findViewById(R.id.row_value);
        v.setText(value);
        v.setTextAppearance(R.style.TextAppearance_K_LabelLg);
        v.setTextColor(ContextCompat.getColor(this, click == null ? R.color.text_muted : R.color.text_primary));
        if (click != null) {
            row.setOnClickListener(click);
        } else {
            row.findViewById(R.id.row_caret).setVisibility(View.GONE);
            row.setBackground(null);
        }
        parent.addView(row);
        return row;
    }

    private interface Toggle { void onToggle(boolean on); }

    @NonNull
    private View switchRow(@NonNull LinearLayout parent, @StringRes int title, @StringRes int sub, boolean checked,
                           @NonNull Toggle onToggle) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_k_switch_row, parent, false);
        ((TextView) row.findViewById(R.id.switch_title)).setText(title);
        TextView s = row.findViewById(R.id.switch_sub);
        if (sub != 0) {
            s.setText(sub);
            s.setVisibility(View.VISIBLE);
        }
        MaterialSwitch sw = row.findViewById(R.id.switch_toggle);
        sw.setChecked(checked);
        row.setContentDescription(getString(title) + (sub != 0 ? ". " + getString(sub) : "") + ", "
                + getString(checked ? R.string.set_k_on : R.string.set_k_off));
        row.setOnClickListener(v -> {
            if (!v.isEnabled()) return;
            sw.setChecked(!sw.isChecked());
            onToggle.onToggle(sw.isChecked());
            Haptics.tick(this);              // after the toggle, so turning Haptics on buzzes
            v.setContentDescription(getString(title) + (sub != 0 ? ". " + getString(sub) : "") + ", "
                    + getString(sw.isChecked() ? R.string.set_k_on : R.string.set_k_off));
        });
        parent.addView(row);
        return row;
    }

    private interface Pick { void onPick(int index); }

    private void segmentRow(@NonNull LinearLayout parent, @StringRes int label, @NonNull int[] options, int checked,
                            @Nullable boolean[] enabled, @Nullable String note, @NonNull Pick pick) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_k_segment_row, parent, false);
        ((TextView) row.findViewById(R.id.seg_label)).setText(label);
        SegmentedToggle group = row.findViewById(R.id.seg_group);
        int[] ids = {R.id.seg_0, R.id.seg_1, R.id.seg_2};
        for (int i = 0; i < ids.length; i++) {
            MaterialButton b = row.findViewById(ids[i]);
            b.setText(options[i]);
            if (enabled != null && !enabled[i]) {
                b.setEnabled(false);
                b.setAlpha(0.45f);
            }
        }
        group.check(ids[checked]);
        group.addOnButtonCheckedListener((g, id, isChecked) -> {
            if (!isChecked) return;
            for (int i = 0; i < ids.length; i++) if (ids[i] == id && i != checked) pick.onPick(i);
        });
        TextView n = row.findViewById(R.id.seg_note);
        n.setText(note);
        n.setVisibility(note == null ? View.GONE : View.VISIBLE);
        parent.addView(row);
    }

    // ── Phone-only choices ──────────────────────────────────────────────────

    private void askIntention() {
        askNumber(R.string.set_k_intention, app.dailyIntention(), 1, 1_000_000, n -> {
            app.setDailyIntention(n);
            render();
        });
    }

    private void pickTasbihTarget() {
        int now = app.tasbihTarget();
        ChoiceSheet s = ChoiceSheet.with(this).title(R.string.set_k_tasbih_target).subtitle(R.string.set_k_tasbih_sub);
        boolean preset = false;
        for (int t : TASBIH_TARGETS) {
            preset |= t == now;
            s.option(0, Numbers.format(t), null, t == now, () -> {
                app.setTasbihTarget(t);
                render();
            });
        }
        s.option(R.drawable.ic_ph_pencil_simple, getString(R.string.circle_goal_custom),
                preset ? null : Numbers.format(now), !preset,
                () -> askNumber(R.string.set_k_tasbih_target, now, 1, 99_999, n -> {
                    app.setTasbihTarget(n);
                    render();
                }));
        s.show();
    }

    private interface NumberDone { void onNumber(int n); }

    private void askNumber(@StringRes int title, int current, int min, int max, @NonNull NumberDone done) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_number, null, false);
        TextInputLayout layout = v.findViewById(R.id.number_layout);
        TextInputEditText input = v.findViewById(R.id.number_input);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(String.valueOf(max).length())});
        layout.setPlaceholderText(null);
        input.setText(String.valueOf(current));
        input.setSelection(input.length());
        AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(title)
                .setView(v)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            int n;
            try {
                n = Integer.parseInt(String.valueOf(input.getText()).trim());
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < min || n > max) {
                layout.setError(getString(R.string.set_k_number_range, Numbers.format(min), Numbers.format(max)));
                return;
            }
            d.dismiss();
            done.onNumber(n);
        }));
        d.show();
    }

    // ── Server-backed choices ───────────────────────────────────────────────

    @NonNull
    private String clock(@Nullable String hhmm) {
        try {
            LocalTime t = LocalTime.parse(hhmm == null ? "21:00" : hhmm);
            return t.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT));
        } catch (RuntimeException e) {
            return hhmm == null ? "" : hhmm;
        }
    }

    private void pickReminder(@NonNull dto.Reminders r) {
        if (!loaded) return;
        ChoiceSheet.with(this).title(R.string.set_k_reminder_time).subtitle(R.string.settings_reminder_sub)
                .option(R.drawable.ic_ph_clock_counter_clockwise, getString(R.string.set_k_pick_time),
                        r.enabled ? clock(r.time) : null, r.enabled, () -> pickTime(r))
                .option(R.drawable.ic_ph_minus, getString(R.string.set_k_off), null, !r.enabled, () -> {
                    if (!r.enabled) return;
                    save(nested("reminders", "enabled", false), () -> r.enabled = false, () -> r.enabled = true);
                })
                .show();
    }

    private void pickTime(@NonNull dto.Reminders r) {
        LocalTime t;
        try {
            t = LocalTime.parse(r.time == null ? "21:00" : r.time);
        } catch (RuntimeException e) {
            t = LocalTime.of(21, 0);
        }
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(DateFormat.is24HourFormat(this) ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(t.getHour())
                .setMinute(t.getMinute())
                .setTitleText(R.string.set_k_reminder_time)
                .build();
        picker.addOnPositiveButtonClickListener(x -> {
            String hhmm = String.format(java.util.Locale.US, "%02d:%02d", picker.getHour(), picker.getMinute());
            String oldTime = r.time;
            boolean oldOn = r.enabled;
            JsonObject inner = new JsonObject();
            inner.addProperty("enabled", true);
            inner.addProperty("time", hhmm);
            JsonObject p = new JsonObject();
            p.add("reminders", inner);
            save(wrap(p), () -> {
                r.enabled = true;
                r.time = hhmm;
            }, () -> {
                r.enabled = oldOn;
                r.time = oldTime;
            });
        });
        picker.show(getSupportFragmentManager(), "reminder_time");
    }

    private void setLanguage(@NonNull String tag) {
        if (tag.equals(app.lang())) return;
        Haptics.tick(this);
        JsonObject body = new JsonObject();
        body.addProperty("lang", tag);
        ApiClient.get(getApplicationContext()).updatePreferences(body).enqueue(new Callback<dto.UserDto>() {
            @Override public void onResponse(@NonNull Call<dto.UserDto> c, @NonNull Response<dto.UserDto> r) { }

            @Override public void onFailure(@NonNull Call<dto.UserDto> c, @NonNull Throwable t) { }
        });
        app.setLang(tag);          // applies the per-app locale and recreates this screen
    }

    private void pickMethod() {
        ChoiceSheet sheet = ChoiceSheet.with(this).title(R.string.settings_prayer_method)
                .subtitle(R.string.settings_prayer_method_sub);
        for (int id : METHOD_IDS) {
            sheet.option(0, methodName(id), null, id == prefs.prayerMethod, () -> setPrayer(id, prefs.prayerSchool));
        }
        sheet.show();
    }

    private void pickSchool() {
        ChoiceSheet.with(this).title(R.string.settings_prayer_asr)
                .option(0, R.string.settings_asr_hanafi, R.string.settings_asr_hanafi_sub,
                        prefs.prayerSchool == 1, () -> setPrayer(prefs.prayerMethod, 1))
                .option(0, R.string.settings_asr_shafi, R.string.settings_asr_shafi_sub,
                        prefs.prayerSchool == 0, () -> setPrayer(prefs.prayerMethod, 0))
                .show();
    }

    private void setPrayer(int method, int school) {
        int oldMethod = prefs.prayerMethod, oldSchool = prefs.prayerSchool;
        if (method == oldMethod && school == oldSchool) return;
        // The phone's copy changes at once (Home refetches with it); the server copy follows.
        app.setPrayer(method, school);
        JsonObject p = new JsonObject();
        p.addProperty("prayer_method", method);
        p.addProperty("prayer_school", school);
        save(wrap(p), () -> {
            prefs.prayerMethod = method;
            prefs.prayerSchool = school;
        }, () -> {
            prefs.prayerMethod = oldMethod;
            prefs.prayerSchool = oldSchool;
            app.setPrayer(oldMethod, oldSchool);
        });
    }

    /** Applies {@code apply} now, PATCHes {@code body}, runs {@code undo} if that fails. */
    private void save(@NonNull JsonObject body, @NonNull Runnable apply, @NonNull Runnable undo) {
        apply.run();
        render();
        busy(+1);
        ApiClient.get(this).updatePreferences(body).enqueue(new Callback<dto.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.UserDto> call, @NonNull Response<dto.UserDto> r) {
                busy(-1);
                if (isFinishing()) return;
                if (!r.isSuccessful()) failed(undo);
            }

            @Override
            public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) {
                busy(-1);
                if (!isFinishing()) failed(undo);
            }
        });
    }

    private void failed(@NonNull Runnable undo) {
        undo.run();
        render();
        Haptics.reject(this);
        Snackbar.make(findViewById(R.id.settings_scroll), R.string.settings_save_failed, Snackbar.LENGTH_LONG).show();
    }

    @NonNull
    private static JsonObject field(@NonNull String key, boolean value) {
        JsonObject p = new JsonObject();
        p.addProperty(key, value);
        return wrap(p);
    }

    @NonNull
    private static JsonObject nested(@NonNull String group, @NonNull String key, boolean value) {
        JsonObject inner = new JsonObject();
        inner.addProperty(key, value);
        JsonObject p = new JsonObject();
        p.add(group, inner);
        return wrap(p);
    }

    @NonNull
    private static JsonObject wrap(@NonNull JsonObject preferences) {
        JsonObject body = new JsonObject();
        body.add("preferences", preferences);
        return body;
    }

    private void busy(int delta) {
        saving = Math.max(0, saving + delta);
        findViewById(R.id.settings_progress).setVisibility(saving > 0 ? View.VISIBLE : View.INVISIBLE);
    }

    // ── Account and about ───────────────────────────────────────────────────

    private void openAvatarStudio() {
        avatarStudio.launch(AvatarStudioActivity.intent(this, new TokenStore(this).avatar(), false));
    }

    private void saveAvatar(@NonNull String spec) {
        android.content.Context ctx = getApplicationContext();
        ApiClient.get(ctx).updateMe(dto.UserUpdateRequest.avatar(spec)).enqueue(new Callback<dto.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.UserDto> call, @NonNull Response<dto.UserDto> r) {
                if (r.isSuccessful()) {
                    new TokenStore(ctx).setAvatar(spec);
                    if (!isFinishing()) bindAccount();
                } else if (!isFinishing()) {
                    Snackbar.make(findViewById(R.id.settings_scroll), R.string.onb_avatar_failed, Snackbar.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) {
                if (!isFinishing()) Snackbar.make(findViewById(R.id.settings_scroll), R.string.onb_avatar_failed, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void rate() {
        String pkg = getPackageName();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
        }
    }

    @NonNull
    private String methodName(int id) {
        switch (id) {
            case 1: return getString(R.string.method_karachi);
            case 2: return getString(R.string.method_isna);
            case 3: return getString(R.string.method_mwl);
            case 4: return getString(R.string.method_umm_al_qura);
            case 5: return getString(R.string.method_egypt);
            case 8: return getString(R.string.method_gulf);
            case 15: return getString(R.string.method_moonsighting);
            case 16: return getString(R.string.method_dubai);
            default: return getString(R.string.method_other, id);
        }
    }
}
