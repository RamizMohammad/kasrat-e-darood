package in.mohammad.ramiz.islamic.kasrat_e_darrod;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups.GroupsFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.home.HomeFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library.LibraryFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile.ProfileFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.stats.StatsFragment;

/** Hosts the five primary destinations behind the bottom navigation bar. */
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, bars.top, 0, 0);
            return insets;
        });

        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            Fragment fragment;
            if (id == R.id.nav_library) {
                fragment = new LibraryFragment();
            } else if (id == R.id.nav_groups) {
                fragment = new GroupsFragment();
            } else if (id == R.id.nav_stats) {
                fragment = new StatsFragment();
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

    private void show(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.nav_host, fragment)
                .commit();
    }
}
