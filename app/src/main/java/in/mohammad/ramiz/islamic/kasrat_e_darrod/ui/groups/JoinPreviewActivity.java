package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.GroupAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks.RankRows;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Circle invite (Stitch join_circle_preview_teal): what the code leads to, then "Join circle"
 * (POST /groups/join { invite_code }). Joined → the circle's home; "requested" (a circle that
 * approves each member) → the button says the request is sent.
 */
public class JoinPreviewActivity extends BaseActivity {

    private static final String EXTRA_CODE = "code", EXTRA_GROUP = "group";
    private static final Gson GSON = new Gson();

    @NonNull
    public static Intent intent(@NonNull Context ctx, @NonNull String code, @NonNull dto.GroupDetail g) {
        return new Intent(ctx, JoinPreviewActivity.class)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_GROUP, GSON.toJson(g));
    }

    /** A public circle opened by id (no code): Join calls POST /groups/{id}/join. */
    @NonNull
    public static Intent intentPublic(@NonNull Context ctx, @NonNull dto.GroupDetail g) {
        return new Intent(ctx, JoinPreviewActivity.class).putExtra(EXTRA_GROUP, GSON.toJson(g));
    }

    private String code;
    private dto.GroupDetail group;
    private MaterialButton join;
    private TextView error;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_join_preview);
        EdgeInsets.padSystemBars(findViewById(R.id.root), false);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        code = getIntent().getStringExtra(EXTRA_CODE);
        try {
            group = GSON.fromJson(getIntent().getStringExtra(EXTRA_GROUP), dto.GroupDetail.class);
        } catch (RuntimeException e) {
            group = null;
        }
        if (group == null || group.id == null) {
            finish();
            return;
        }
        join = findViewById(R.id.preview_join);
        error = findViewById(R.id.preview_error);
        join.setOnClickListener(v -> join());
        bind(group);
    }

    private void bind(@NonNull dto.GroupDetail g) {
        ((GroupAvatarView) findViewById(R.id.preview_avatar)).setGroup(g.avatar, g.id, g.isGlobal);
        ((TextView) findViewById(R.id.preview_name)).setText(g.name);
        ((TextView) findViewById(R.id.preview_meta)).setText(getString(R.string.circles_members_privacy,
                Groups.members(this, g.memberCount), getString(Groups.privacyLabel(g.privacy))));
        bindFaces(g.top, g.memberCount);

        boolean hasGoal = g.weeklyGoal != null && g.weeklyGoal > 0;
        TextView week = findViewById(R.id.preview_week);
        LinearProgressIndicator bar = findViewById(R.id.preview_bar);
        if (hasGoal) {
            week.setText(getString(R.string.circles_of_goal, Numbers.format(g.weekTotal), Numbers.format(g.weeklyGoal)));
            bar.setVisibility(View.VISIBLE);
            bar.setProgressCompat((int) Math.min(1000, g.weekTotal * 1000 / g.weeklyGoal), false);
        } else {
            week.setText(Numbers.format(g.weekTotal));
            bar.setVisibility(View.GONE);
        }
        TextView started = findViewById(R.id.preview_started);
        String when = monthYear(g.createdAt);
        started.setVisibility(when == null ? View.GONE : View.VISIBLE);
        if (when != null) started.setText(getString(R.string.circles_started, when));

        if (g.isMember() || "member".equals(g.viewerStatus)) {
            join.setText(R.string.circles_open);
            join.setOnClickListener(v -> openCircle());
        } else if ("requested".equals(g.viewerStatus)) {
            requested();
        } else if (g.full) {
            join.setEnabled(false);
            join.setText(R.string.groups_err_full);
        }
    }

    /** Up to four faces from the week's top members, then "+N" for the rest (36dp, white ring). */
    private void bindFaces(List<dto.BoardEntry> top, int members) {
        LinearLayout row = findViewById(R.id.preview_faces);
        row.removeAllViews();
        float dp = getResources().getDisplayMetrics().density;
        int shown = 0;
        if (top != null) {
            for (dto.BoardEntry e : top) {
                if (shown == 4) break;
                FrameLayout ring = face(row, shown, dp);
                AuraAvatarView av = new AuraAvatarView(this);
                ring.addView(av, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
                RankRows.avatar(av, e);
                shown++;
            }
        }
        int rest = members - shown;
        if (rest > 0 && shown > 0) {
            FrameLayout ring = face(row, shown, dp);
            ring.setBackgroundResource(R.drawable.bg_k_more_face);
            TextView t = new TextView(this);
            t.setText(getString(R.string.circles_more, rest));
            t.setTextAppearance(R.style.TextAppearance_K_LabelSm);
            t.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            t.setGravity(Gravity.CENTER);
            ring.addView(t, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }
        row.setVisibility(row.getChildCount() == 0 ? View.GONE : View.VISIBLE);
    }

    @NonNull
    private FrameLayout face(@NonNull LinearLayout row, int index, float dp) {
        FrameLayout ring = new FrameLayout(this);
        ring.setBackgroundResource(R.drawable.bg_k_avatar_stack);
        int pad = (int) (2 * dp);
        ring.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (36 * dp), (int) (36 * dp));
        if (index > 0) lp.setMarginStart((int) (-10 * dp));
        row.addView(ring, lp);
        return ring;
    }

    private static String monthYear(String iso) {
        if (iso == null) return null;
        try {
            return OffsetDateTime.parse(iso).format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void join() {
        if (busy) return;
        busy = true;
        error.setVisibility(View.GONE);
        join.setEnabled(false);
        join.setText(R.string.circles_joining);
        Call<dto.JoinResult> call;
        if (code != null) {
            JsonObject body = new JsonObject();
            body.addProperty("invite_code", code);
            call = ApiClient.get(this).joinByCode(body);
        } else {
            call = ApiClient.get(this).joinPublicGroup(group.id);
        }
        call.enqueue(new Callback<dto.JoinResult>() {
            @Override
            public void onResponse(@NonNull Call<dto.JoinResult> call, @NonNull Response<dto.JoinResult> r) {
                if (isFinishing()) return;
                busy = false;
                if (r.isSuccessful() && r.body() != null) {
                    Haptics.click(JoinPreviewActivity.this);
                    if ("requested".equals(r.body().status)) requested();
                    else openCircle();
                } else {
                    join.setEnabled(true);
                    join.setText(R.string.circles_join_button);
                    showError(Groups.error(JoinPreviewActivity.this, r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.JoinResult> call, @NonNull Throwable t) {
                if (isFinishing()) return;
                busy = false;
                join.setEnabled(true);
                join.setText(R.string.circles_join_button);
                showError(getString(R.string.error_network));
            }
        });
    }

    private void requested() {
        join.setEnabled(false);
        join.setText(R.string.circles_requested);
        ((TextView) findViewById(R.id.preview_note)).setText(R.string.circles_requested_note);
    }

    private void openCircle() {
        startActivity(GroupActivity.intent(this, group.id));
        finish();
    }

    private void showError(@NonNull String text) {
        Haptics.reject(this);
        error.setText(text);
        error.setVisibility(View.VISIBLE);
    }
}
