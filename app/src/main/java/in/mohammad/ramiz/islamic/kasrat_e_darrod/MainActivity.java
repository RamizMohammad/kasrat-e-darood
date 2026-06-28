package in.mohammad.ramiz.islamic.kasrat_e_darrod;

import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.home.HomeFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library.LibraryFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile.ProfileFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.stats.LeaderboardFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.stats.StatsFragment;

/** Hosts the four primary destinations behind the bottom navigation bar. */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView nav;

    private final androidx.activity.result.ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {});

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Ask for notification permission on Android 13+ (needed to show pushes).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        nav = findViewById(R.id.bottom_nav);
        // Let the Profile tab show the real (colored) photo instead of a tint.
        nav.setItemIconTintList(null);

        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment;
            if (id == R.id.nav_library) {
                fragment = new LibraryFragment();
            } else if (id == R.id.nav_stats) {
                fragment = new StatsFragment();
            } else if (id == R.id.nav_leaderboard) {
                fragment = new LeaderboardFragment();
            } else if (id == R.id.nav_profile) {
                fragment = new ProfileFragment();
            } else {
                fragment = new HomeFragment();
            }
            show(fragment);
            return true;
        });

        if (savedInstanceState == null) {
            nav.setSelectedItemId(R.id.nav_home);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProfileNavIcon();   // reflect a freshly uploaded photo
    }

    /** Loads the user's circular photo (or default) as the Profile tab icon. */
    private void loadProfileNavIcon() {
        String url = new TokenStore(this).photoUrl();
        int size = (int) (getResources().getDisplayMetrics().density * 28);
        Glide.with(this)
                .asDrawable()
                .load(url == null || url.isEmpty() ? null : url)
                .circleCrop()
                .placeholder(R.drawable.default_profile)
                .fallback(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .override(size, size)
                .into(new CustomTarget<Drawable>() {
                    @Override
                    public void onResourceReady(@NonNull Drawable resource,
                                                @Nullable Transition<? super Drawable> t) {
                        if (nav != null && nav.getMenu().findItem(R.id.nav_profile) != null) {
                            nav.getMenu().findItem(R.id.nav_profile).setIcon(resource);
                        }
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {}
                });
    }

    private void show(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host, fragment)
                .commit();
    }
}
