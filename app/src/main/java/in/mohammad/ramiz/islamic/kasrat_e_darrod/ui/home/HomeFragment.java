package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.home;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.ActivityItem;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan.AlAdhanClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan.AlAdhanDto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.ActivityAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.SkeletonAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.LocationHelper;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.RelativeTime;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Home dashboard. The "Today's Date" and "Prayer Times" cards show a shimmering
 * skeleton while their data loads from the AlAdhan API, then swap to real
 * content. No static template values are displayed.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "NoorPrayer";
    private View root;
    private boolean loaded = false;
    private RecyclerView activityRecycler;

    private final ActivityResultLauncher<String[]> locationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> loadPrayerTimes());   // refine with real location after grant

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        this.root = view;

        bindQuickAdd(view, R.id.quick_yaseen, "Surah Yaseen", "Favorite Recitation", R.drawable.ic_book_open);
        bindQuickAdd(view, R.id.quick_astagh, "Astaghfirullah", "Daily Dhikr", R.drawable.ic_leaf);

        activityRecycler = view.findViewById(R.id.recycler_activity);
        activityRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        activityRecycler.setAdapter(new SkeletonAdapter(3));   // shimmer while loading

        MaterialButton log = view.findViewById(R.id.btn_log_progress);
        log.setOnClickListener(v ->
                Toast.makeText(getContext(), "Log a recitation", Toast.LENGTH_SHORT).show());

        // Local, instant: days/hours until next Jumu'ah (Friday).
        bindJumuahCountdown();

        // Live dashboard: personal goal + recent group activity.
        loadDashboard();

        // Start the skeleton shimmer, then fetch.
        Skeleton.shimmer(view.findViewById(R.id.date_skeleton));
        Skeleton.shimmer(view.findViewById(R.id.prayer_skeleton));
        loadPrayerTimes();

        // Ask for location to refine the fetch (a grant re-runs loadPrayerTimes()).
        if (!LocationHelper.hasPermission(requireContext())) {
            locationPermission.launch(new String[]{
                    android.Manifest.permission.ACCESS_FINE_LOCATION,
                    android.Manifest.permission.ACCESS_COARSE_LOCATION});
        }
    }

    private void loadPrayerTimes() {
        if (!isAdded()) return;
        double[] loc = LocationHelper.lastKnownOrDefault(requireContext());
        String date = new SimpleDateFormat("dd-MM-yyyy", Locale.US).format(new Date());
        Log.d(TAG, "Requesting timings date=" + date + " lat=" + loc[0] + " lon=" + loc[1]);

        AlAdhanClient.get().timings(date, loc[0], loc[1],
                AlAdhanClient.DEFAULT_METHOD, AlAdhanClient.DEFAULT_SCHOOL)
                .enqueue(new Callback<AlAdhanDto.Response>() {
                    @Override
                    public void onResponse(@NonNull Call<AlAdhanDto.Response> call,
                                           @NonNull Response<AlAdhanDto.Response> response) {
                        if (!isAdded()) return;
                        if (!response.isSuccessful() || response.body() == null
                                || response.body().data == null) {
                            Log.e(TAG, "Unsuccessful response: code=" + response.code());
                            revealCards();
                            toast("Prayer times unavailable (HTTP " + response.code() + ")");
                            return;
                        }
                        Log.d(TAG, "Timings received, updating UI");
                        bindPrayerData(response.body().data);
                        revealCards();
                    }

                    @Override
                    public void onFailure(@NonNull Call<AlAdhanDto.Response> call, @NonNull Throwable t) {
                        Log.e(TAG, "Timings request failed", t);
                        if (!isAdded()) return;
                        revealCards();
                        toast("Prayer times failed: " + t.getMessage());
                    }
                });
    }

    /** Swap both skeletons for their content (idempotent). */
    private void revealCards() {
        if (loaded || root == null) return;
        loaded = true;
        Skeleton.reveal(root.findViewById(R.id.date_skeleton), root.findViewById(R.id.date_content));
        Skeleton.reveal(root.findViewById(R.id.prayer_skeleton), root.findViewById(R.id.prayer_content));
    }

    private void bindPrayerData(AlAdhanDto.Data data) {
        AlAdhanDto.Timings t = data.timings;
        if (t != null) {
            setPrayer(R.id.row_fajr, "Fajr", clean(t.fajr));
            setPrayer(R.id.row_dhuhr, "Dhuhr", clean(t.dhuhr));
            setPrayer(R.id.row_asr, "Asr", clean(t.asr));
            setPrayer(R.id.row_maghrib, "Maghrib", clean(t.maghrib));
            setPrayer(R.id.row_isha, "Isha", clean(t.isha));
        }

        if (data.date != null && data.date.hijri != null) {
            AlAdhanDto.Hijri h = data.date.hijri;
            String month = h.month != null ? h.month.en : "";
            text(R.id.txt_hijri_date, (h.day + " " + month + " " + h.year).trim());
        }
        if (data.date != null && data.date.gregorian != null) {
            AlAdhanDto.Gregorian g = data.date.gregorian;
            String weekday = g.weekday != null ? g.weekday.en : "";
            String month = g.month != null ? g.month.en : "";
            text(R.id.txt_greg_date, (weekday + ", " + g.day + " " + month + " " + g.year).trim());
        }
        if (data.meta != null && data.meta.timezone != null) {
            text(R.id.txt_prayer_location, "PRAYER TIMES · " + data.meta.timezone);
        }

        // Show the fasting chip only during Ramadan (Hijri month 9).
        TextView fasting = root.findViewById(R.id.txt_fasting);
        if (fasting != null) {
            boolean ramadan = data.date != null && data.date.hijri != null
                    && data.date.hijri.month != null && data.date.hijri.month.number == 9;
            if (ramadan) {
                fasting.setVisibility(View.VISIBLE);
                fasting.setText("Fasting: Day " + data.date.hijri.day);
            } else {
                fasting.setVisibility(View.GONE);
            }
        }
    }

    /** Loads the dashboard (community group) for the goal ring + recent activity. */
    private void loadDashboard() {
        ApiClient.get(requireContext()).dashboard(null)
                .enqueue(new Callback<dto.DashboardResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.DashboardResponse> call,
                                           @NonNull Response<dto.DashboardResponse> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            bindDashboard(response.body());
                        } else {
                            activityRecycler.setAdapter(
                                    new ActivityAdapter(new ArrayList<>()));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.DashboardResponse> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        activityRecycler.setAdapter(new ActivityAdapter(new ArrayList<>()));
                    }
                });
    }

    private void bindDashboard(dto.DashboardResponse d) {
        if (d.goal != null) {
            text(R.id.goal_percent, d.goal.percent + "%");
            text(R.id.goal_subtitle, d.goal.progress + " of " + d.goal.target + " this week");
        }

        List<ActivityItem> items = new ArrayList<>();
        if (d.recentActivity != null) {
            int i = 0;
            for (dto.ActivityDto a : d.recentActivity) {
                String meta = a.type != null ? a.type.replace('_', ' ') : "";
                items.add(new ActivityItem(
                        a.initial != null ? a.initial : "•",
                        a.text,
                        meta,
                        RelativeTime.from(a.createdAt),
                        (i++ % 2) == 1));   // alternate gold/green avatars
            }
        }
        activityRecycler.setAdapter(new ActivityAdapter(items));
    }

    /** Days and hours remaining until the next Friday (Jumu'ah). */
    private void bindJumuahCountdown() {
        Calendar now = Calendar.getInstance();
        Calendar friday = (Calendar) now.clone();
        int delta = (Calendar.FRIDAY - now.get(Calendar.DAY_OF_WEEK) + 7) % 7;
        friday.add(Calendar.DAY_OF_MONTH, delta);
        friday.set(Calendar.HOUR_OF_DAY, 13);   // approximate Jumu'ah time
        friday.set(Calendar.MINUTE, 0);
        friday.set(Calendar.SECOND, 0);
        long millis = friday.getTimeInMillis() - now.getTimeInMillis();
        if (millis < 0) millis += 7L * 24 * 60 * 60 * 1000;  // already past today's Jumu'ah
        long totalHours = millis / (60 * 60 * 1000);
        text(R.id.txt_jumuah_days, String.format(Locale.US, "%02d", totalHours / 24));
        text(R.id.txt_jumuah_hours, String.format(Locale.US, "%02d", totalHours % 24));
    }

    /** Strips any trailing timezone annotation like "06:03 (UTC)" -> "06:03". */
    private String clean(String time) {
        if (time == null) return "--:--";
        int space = time.indexOf(' ');
        return space > 0 ? time.substring(0, space) : time;
    }

    private void setPrayer(int includeId, String name, String time) {
        View row = root.findViewById(includeId);
        if (row == null) return;
        ((TextView) row.findViewById(R.id.prayer_name)).setText(name);
        ((TextView) row.findViewById(R.id.prayer_time)).setText(time);
    }

    private void text(int id, String value) {
        TextView tv = root.findViewById(id);
        if (tv != null) tv.setText(value);
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
    }

    private void bindQuickAdd(View parent, int includeId, String title, String subtitle, int icon) {
        View card = parent.findViewById(includeId);
        ((TextView) card.findViewById(R.id.quick_title)).setText(title);
        ((TextView) card.findViewById(R.id.quick_subtitle)).setText(subtitle);
        ((ImageView) card.findViewById(R.id.quick_icon)).setImageResource(icon);
    }
}
