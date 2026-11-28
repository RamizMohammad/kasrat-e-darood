package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.RecitationStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Recitation detail (Stitch darood_e_ibrahimi_teal): the full text, your stats for it,
 * the source, "Start tasbih" and "Add to log".
 *
 * Stats come from your last 100 logs: total for this recitation, this week (since
 * Friday, when boards close) and your best day. If you have 100 or more logs the
 * first one is labelled "Last 100 logs" rather than "Your total".
 */
public class RecitationDetailActivity extends BaseActivity implements Cart.Listener {

    private static final String EXTRA_ID = "recitation_id";

    @NonNull
    public static Intent intent(@NonNull Context ctx, @NonNull String recitationId) {
        return new Intent(ctx, RecitationDetailActivity.class).putExtra(EXTRA_ID, recitationId);
    }

    private dto.RecitationDto rec;
    private String lang;
    private MaterialButton add, heart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recitation_detail);
        EdgeInsets.padTop(findViewById(R.id.root));
        EdgeInsets.padBottom(findViewById(R.id.detail_actions), false);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        lang = new AppPrefs(this).lang();
        add = findViewById(R.id.detail_add);
        heart = findViewById(R.id.detail_heart);

        String id = getIntent().getStringExtra(EXTRA_ID);
        RecitationStore store = RecitationStore.get(this);
        dto.RecitationDto r = store.byId(id);
        if (r != null) {
            bind(r, savedInstanceState == null);
        } else {
            store.load(new RecitationStore.Callback() {
                @Override public void onList(@NonNull List<dto.RecitationDto> list, boolean fromCache) {
                    dto.RecitationDto found = store.byId(id);
                    if (found != null && rec == null && !isFinishing()) bind(found, true);
                }

                @Override public void onError(boolean offline) {
                    if (rec == null && !isFinishing()) {
                        Toast.makeText(RecitationDetailActivity.this, R.string.error_network, Toast.LENGTH_LONG).show();
                        finish();
                    }
                }
            });
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Cart.get().addListener(this);
        onCartChanged();
    }

    @Override
    protected void onStop() {
        Cart.get().removeListener(this);
        super.onStop();
    }

    @Override
    public void onCartChanged() {
        if (rec == null) return;
        int n = Cart.get().countFor(rec.id);
        add.setText(n > 0 ? getString(R.string.detail_add_log_n, Numbers.format(n))
                : getString(R.string.detail_add_log));
    }

    private void bind(@NonNull dto.RecitationDto r, boolean animate) {
        rec = r;
        String title = RecitationStore.title(r, lang);
        text(R.id.detail_category, r.category);
        text(R.id.detail_arabic, r.arabicName);
        text(R.id.detail_transliteration, r.transliteration);
        text(R.id.detail_name, title);
        text(R.id.detail_meaning, r.translation);
        text(R.id.detail_reference, r.reference);
        findViewById(R.id.detail_reference_card).setVisibility(
                TextUtils.isEmpty(r.reference) ? View.GONE : View.VISIBLE);
        bindHeart();
        heart.setOnClickListener(v -> {
            Haptics.tick(this);
            new in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Favourites(this).toggle(r.id);
            bindHeart();
        });

        int step = RecitationStore.step(r);
        add.setOnClickListener(v -> {
            Haptics.tick(this);
            Cart.get().add(r.id, r.arabicName, title, step);
        });
        findViewById(R.id.detail_tasbih).setOnClickListener(v ->
                startActivity(TasbihActivity.intent(this, r.id)));
        onCartChanged();
        loadYours(r.id);
        if (animate) Motion.enterStack(findViewById(R.id.detail_column));
    }

    private void bindHeart() {
        boolean fav = new in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Favourites(this).has(rec.id);
        heart.setIconTint(android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, fav ? R.color.amber : R.color.text_muted)));
        heart.setContentDescription(getString(fav ? R.string.recite_fav_remove : R.string.recite_fav_add));
    }

    private void loadYours(@NonNull String id) {
        ApiClient.get(this).recentSubmissions(100).enqueue(new Callback<List<dto.MySubmissionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.MySubmissionDto>> call,
                                   @NonNull Response<List<dto.MySubmissionDto>> r) {
                if (isFinishing() || !r.isSuccessful() || r.body() == null) return;
                long weekStart = fridayStart();
                long total = 0, week = 0;
                java.util.Map<String, Long> byDay = new java.util.HashMap<>();
                java.text.SimpleDateFormat day = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
                for (dto.MySubmissionDto s : r.body()) {
                    if (!id.equals(s.recitationId)) continue;
                    total += s.count;
                    long t = in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.IsoTime.parse(s.createdAt);
                    if (t <= 0) continue;
                    if (t >= weekStart) week += s.count;
                    byDay.merge(day.format(new java.util.Date(t)), (long) s.count, Long::sum);
                }
                long best = 0;
                for (long v : byDay.values()) best = Math.max(best, v);
                ((TextView) findViewById(R.id.detail_stat_total)).setText(Numbers.format(total));
                ((TextView) findViewById(R.id.detail_stat_week)).setText(Numbers.format(week));
                ((TextView) findViewById(R.id.detail_stat_best)).setText(Numbers.format(best));
                ((TextView) findViewById(R.id.detail_stat_total_label)).setText(
                        r.body().size() >= 100 ? R.string.detail_stat_recent : R.string.detail_stat_total);
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.MySubmissionDto>> call, @NonNull Throwable t) { }
        });
    }

    /** Start of the current week: the last Friday 00:00 (boards close every Friday). */
    private static long fridayStart() {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, 0);
        c.set(java.util.Calendar.MINUTE, 0);
        c.set(java.util.Calendar.SECOND, 0);
        c.set(java.util.Calendar.MILLISECOND, 0);
        int back = (c.get(java.util.Calendar.DAY_OF_WEEK) - java.util.Calendar.FRIDAY + 7) % 7;
        c.add(java.util.Calendar.DAY_OF_MONTH, -back);
        return c.getTimeInMillis();
    }

    private void text(int viewId, @Nullable String value) {
        TextView tv = findViewById(viewId);
        tv.setText(value);
        tv.setVisibility(TextUtils.isEmpty(value) ? View.GONE : View.VISIBLE);
    }
}
