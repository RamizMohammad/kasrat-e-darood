package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan.AlAdhanClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan.AlAdhanDto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.notifications.NotificationsActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.LocationHelper;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Home dashboard. The "Today's Date" and "Prayer Times" cards show a shimmering
 * skeleton while their data loads from the AlAdhan API, then swap to real
 * content, plus a local Jumu'ah countdown. The bell opens the Notifications screen.
 */
public class HomeFragment extends Fragment {

    private static final String TAG = "NoorPrayer";
    private View root;
    private boolean loaded = false;

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

        // Real user name + avatar in the header.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore store =
                new in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore(requireContext());
        String name = store.userName();
        TextView welcome = view.findViewById(R.id.txt_welcome);
        if (name != null && !name.isEmpty()) {
            welcome.setText(getString(R.string.welcome_back, name));
        }
        in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Avatars.load(view.findViewById(R.id.home_avatar));

        view.findViewById(R.id.home_bell).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), NotificationsActivity.class)));

        // Local, instant: days/hours until next Jumu'ah (Friday).
        bindJumuahCountdown();

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
}
