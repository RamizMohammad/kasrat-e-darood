package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.gson.JsonObject;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.GroupAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.SimpleTextWatcher;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * New circle (Stitch new_circle_teal), one page: emblem, name, description, weekly goal and
 * privacy, then "Create circle", which opens the new circle's home.
 *
 * Joining is by invite code (join_rule "code"); Stitch's page has no approval step or
 * total-only switch, so new circles use the defaults and those stay in Circle settings.
 */
public class CreateGroupActivity extends BaseActivity {

    /** 25k · 50k · 1 lakh · None (Stitch). */
    private static final long[] GOALS = {25_000, 50_000, 1_00_000, 0};
    private static final long MAX_GOAL = 1_000_000_000L;

    private GroupAvatarPicker avatar;
    private TextInputEditText name, desc;
    private TextInputLayout nameLayout;
    private TextView error, goalValue;
    private LinearLayout chips;
    private View privCard, pubCard;
    private MaterialButton create;
    private boolean isPublic, busy;
    private long goal = 1_00_000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_group);
        EdgeInsets.padSystemBars(findViewById(R.id.root), true);

        avatar = findViewById(R.id.create_avatar);
        name = findViewById(R.id.create_name);
        nameLayout = findViewById(R.id.create_name_layout);
        desc = findViewById(R.id.create_desc);
        error = findViewById(R.id.create_error);
        goalValue = findViewById(R.id.create_goal_value);
        chips = findViewById(R.id.create_goal_chips);
        privCard = findViewById(R.id.create_private);
        pubCard = findViewById(R.id.create_public);
        create = findViewById(R.id.create_next);

        // "Description (optional)": the "(optional)" in warm grey.
        String label = getString(R.string.circles_desc_label), opt = getString(R.string.circles_optional);
        SpannableString sp = new SpannableString(label + " " + opt);
        sp.setSpan(new ForegroundColorSpan(ContextCompat.getColor(this, R.color.text_muted)),
                label.length() + 1, sp.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ((TextView) findViewById(R.id.create_desc_label)).setText(sp);

        if (savedInstanceState != null) {
            goal = savedInstanceState.getLong("goal", goal);
            isPublic = savedInstanceState.getBoolean("public");
        }
        int seed = (int) (System.nanoTime() & 0xffff);
        avatar.setSpec(savedInstanceState != null && savedInstanceState.getString("avatar") != null
                ? GroupAvatarView.Spec.of(savedInstanceState.getString("avatar"), null)
                : new GroupAvatarView.Spec(seed, 0, 0));

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        name.addTextChangedListener(new SimpleTextWatcher(t -> {
            nameLayout.setError(null);
            error.setVisibility(View.GONE);
        }));
        privCard.setOnClickListener(v -> setPublic(false));
        pubCard.setOnClickListener(v -> setPublic(true));
        goalValue.setOnClickListener(v -> askGoal());
        create.setOnClickListener(v -> submit());
        buildChips();
        setPublic(isPublic);
        showGoal();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putLong("goal", goal);
        out.putBoolean("public", isPublic);
        out.putString("avatar", avatar.spec().encode());
    }

    // ── Weekly goal ─────────────────────────────────────────────────────────

    private void buildChips() {
        float dp = getResources().getDisplayMetrics().density;
        for (long g : GOALS) {
            TextView c = new TextView(this);
            c.setText(g == 0 ? getString(R.string.groups_goal_none) : chipLabel(g));
            c.setTag(g);
            c.setTextAppearance(R.style.TextAppearance_K_LabelSm);
            c.setBackgroundResource(R.drawable.bg_k_goal_chip);
            c.setPadding((int) (14 * dp), (int) (6 * dp), (int) (14 * dp), (int) (6 * dp));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd((int) (8 * dp));
            c.setLayoutParams(lp);
            c.setOnClickListener(v -> {
                Haptics.tick(this);
                goal = (long) v.getTag();
                showGoal();
            });
            chips.addView(c);
        }
    }

    /** 25,000 → "25k", 1,00,000 → "1 lakh". */
    @NonNull
    private String chipLabel(long g) {
        if (g % 1_00_000 == 0) return getString(R.string.circles_goal_lakh, g / 1_00_000);
        return getString(R.string.circles_goal_k, g / 1000);
    }

    private void showGoal() {
        goalValue.setText(goal > 0 ? Numbers.format(goal) : getString(R.string.groups_goal_none));
        goalValue.setTextColor(ContextCompat.getColor(this, goal > 0 ? R.color.text_primary : R.color.text_muted));
        for (int i = 0; i < chips.getChildCount(); i++) {
            TextView c = (TextView) chips.getChildAt(i);
            long g = (long) c.getTag();
            boolean on = g == goal;
            c.setSelected(on);
            c.setTextColor(ContextCompat.getColor(this, on ? R.color.surface_lowest
                    : g == 0 ? R.color.text_muted : R.color.text_primary));
        }
    }

    private void askGoal() {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_number, null, false);
        TextInputLayout layout = v.findViewById(R.id.number_layout);
        TextInputEditText input = v.findViewById(R.id.number_input);
        // dialog_number caps at 5 digits (tasbih targets); a weekly goal can be 1 lakh and up.
        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(10)});
        layout.setPlaceholderText(null);
        if (goal > 0) input.setText(String.valueOf(goal));
        androidx.appcompat.app.AlertDialog d = new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.groups_goal_row)
                .setView(v)
                .setPositiveButton(R.string.action_save, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        d.setOnShowListener(x -> d.getButton(android.content.DialogInterface.BUTTON_POSITIVE).setOnClickListener(b -> {
            String t = String.valueOf(input.getText()).trim();
            long n;
            try {
                n = t.isEmpty() ? 0 : Long.parseLong(t);
            } catch (NumberFormatException e) {
                n = -1;
            }
            if (n < 0 || n > MAX_GOAL) {
                layout.setError(getString(R.string.groups_err_goal));
                return;
            }
            goal = n;
            showGoal();
            d.dismiss();
        }));
        d.show();
    }

    // ── Privacy ─────────────────────────────────────────────────────────────

    private void setPublic(boolean on) {
        isPublic = on;
        KChoice.bind(privCard, R.drawable.ic_ph_lock, R.string.groups_private_title, R.string.circles_private_sub, !on);
        KChoice.bind(pubCard, R.drawable.ic_ph_globe, R.string.groups_public_title, R.string.circles_public_sub, on);
    }

    // ── Create ──────────────────────────────────────────────────────────────

    private void submit() {
        if (busy) return;
        String n = Groups.clean(name.getText());
        if (!Groups.validName(n)) {
            nameLayout.setError(getString(R.string.groups_err_name));
            Haptics.reject(this);
            name.requestFocus();
            return;
        }
        JsonObject body = new JsonObject();
        body.addProperty("name", n);
        String d = Groups.clean(desc.getText());
        if (!d.isEmpty()) body.addProperty("description", d);
        body.addProperty("avatar", avatar.spec().encode());
        body.addProperty("privacy", isPublic ? "public" : "private");
        body.addProperty("join_rule", "code");
        if (goal > 0) body.addProperty("weekly_goal", goal);
        body.addProperty("total_only", false);

        setBusy(true);
        ApiClient.get(this).createGroup(body).enqueue(new Callback<dto.GroupDetail>() {
            @Override
            public void onResponse(@NonNull Call<dto.GroupDetail> call, @NonNull Response<dto.GroupDetail> r) {
                if (isFinishing()) return;
                if (r.isSuccessful() && r.body() != null) {
                    Haptics.click(CreateGroupActivity.this);
                    startActivity(GroupActivity.intent(CreateGroupActivity.this, r.body().id));
                    finish();
                } else {
                    setBusy(false);
                    showError(Groups.error(CreateGroupActivity.this, r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.GroupDetail> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                setBusy(false);
                showError(getString(R.string.error_network));
            }
        });
    }

    private void setBusy(boolean on) {
        busy = on;
        create.setEnabled(!on);
        create.setText(on ? R.string.groups_creating : R.string.circles_create_button);
    }

    private void showError(@NonNull String text) {
        Haptics.reject(this);
        error.setText(text);
        error.setVisibility(View.VISIBLE);
        error.announceForAccessibility(text);
    }
}
