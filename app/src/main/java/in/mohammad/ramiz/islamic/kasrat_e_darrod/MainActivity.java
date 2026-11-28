package in.mohammad.ramiz.islamic.kasrat_e_darrod;

import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.databinding.ActivityMainBinding;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.home.HomeFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library.LibraryFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile.MeFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.groups.GroupsFragment;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.ranks.RanksFragment;

/**
 * Shell: Home · Recite · Circles · Ranks · Me on the Stitch floating bottom bar.
 * The bar floats over the tab; every tab draws its own Stitch header and pads its scroll by
 * {@link #navSpace()} (NavSpaceAware). The old shared app header is gone (Phase 8).
 * Other screens can open a tab with {@link #EXTRA_TAB} (e.g. "Go to Recite").
 */
public class MainActivity extends BaseActivity {

    private static final String STATE_TAB = "tab";
    /** Start on (or switch to) this tab, e.g. TAB_RECITE. */
    public static final String EXTRA_TAB = "open_tab";
    private static final int HOME = 0, RECITE = 1, GROUPS = 2, RANKS = 3, ME = 4;
    public static final int TAB_HOME = HOME, TAB_RECITE = RECITE, TAB_GROUPS = GROUPS,
            TAB_RANKS = RANKS, TAB_ME = ME;

    private ActivityMainBinding b;
    private View[] dots;
    private int navSpace;
    private View[] tabs;
    private ImageView[] icons;
    private TextView[] labels;
    private int current = -1;

    private final ActivityResultLauncher<String> notifPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> { });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(b.getRoot());
        placeBottomBar();

        // Android 13+: pushes need permission. (P8 adds an explainer screen first.)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS);
        }

        tabs = new View[]{b.bottomBar.tabHome, b.bottomBar.tabRecite, b.bottomBar.tabGroups,
                b.bottomBar.tabRanks, b.bottomBar.tabMe};
        icons = new ImageView[]{b.bottomBar.iconHome, b.bottomBar.iconRecite, b.bottomBar.iconGroups,
                b.bottomBar.iconRanks, b.bottomBar.iconMe};
        labels = new TextView[]{b.bottomBar.labelHome, b.bottomBar.labelRecite, b.bottomBar.labelGroups,
                b.bottomBar.labelRanks, b.bottomBar.labelMe};
        dots = new View[]{b.bottomBar.dotHome, b.bottomBar.dotRecite, b.bottomBar.dotGroups,
                b.bottomBar.dotRanks, b.bottomBar.dotMe};
        for (int i = 0; i < tabs.length; i++) {
            final int index = i;
            tabs[i].setOnClickListener(v -> {
                if (index != current) Haptics.tick(this);
                select(index, true);
            });
        }

        int start = savedInstanceState != null ? savedInstanceState.getInt(STATE_TAB, HOME)
                : getIntent().getIntExtra(EXTRA_TAB, HOME);
        select(start, savedInstanceState == null);
    }

    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        if (intent.hasExtra(EXTRA_TAB)) openTab(intent.getIntExtra(EXTRA_TAB, HOME));
    }

    private void select(int index, boolean replaceFragment) {
        boolean changed = index != current;
        current = index;
        applyNavPadding();

        // Stitch: current tab = deep-teal icon + label + 4dp dot (py-1 px-3);
        // others = warm-grey icon only (p-2.5).
        int on = ContextCompat.getColor(this, R.color.amber);
        int off = ContextCompat.getColor(this, R.color.text_muted);
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < tabs.length; i++) {
            boolean selected = i == index;
            icons[i].setImageTintList(ColorStateList.valueOf(selected ? on : off));
            labels[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            dots[i].setVisibility(selected ? View.VISIBLE : View.GONE);
            if (selected) tabs[i].setPadding((int) (12 * dp), (int) (4 * dp), (int) (12 * dp), (int) (4 * dp));
            else tabs[i].setPadding((int) (10 * dp), (int) (10 * dp), (int) (10 * dp), (int) (10 * dp));
            tabs[i].setSelected(selected);
        }
        if (changed && replaceFragment) show(fragmentFor(index));
    }

    // ── Floating bottom bar ───────────────────────────────────────────────

    /** 20dp above the gesture / nav bar; navSpace = everything the bar covers. */
    private void placeBottomBar() {
        View bar = b.bottomBar.getRoot();
        final int base = ((android.view.ViewGroup.MarginLayoutParams) bar.getLayoutParams()).bottomMargin;
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(bar, (v, insets) -> {
            int nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom;
            android.view.ViewGroup.MarginLayoutParams lp =
                    (android.view.ViewGroup.MarginLayoutParams) v.getLayoutParams();
            lp.bottomMargin = base + nav;
            v.setLayoutParams(lp);
            return insets;
        });
        bar.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            int space = b.main.getHeight() - v.getTop();
            if (space != navSpace && space > 0) {
                navSpace = space;
                applyNavPadding();
            }
        });
        androidx.core.view.ViewCompat.requestApplyInsets(bar);
    }

    /** Height from the top of the floating bar to the bottom of the screen. */
    public int navSpace() { return navSpace; }

    private void applyNavPadding() {
        androidx.fragment.app.Fragment f = getSupportFragmentManager().findFragmentById(R.id.nav_host);
        if (f instanceof NavSpaceAware) ((NavSpaceAware) f).onNavSpace(navSpace);
    }

    /** Ported tabs implement this to pad their scroll so the last card clears the bar. */
    public interface NavSpaceAware {
        void onNavSpace(int px);
    }

    /** Switch tabs from inside a screen (Home's group card → Ranks, avatar → Me). */
    public void openTab(int index) {
        if (index != current) Haptics.tick(this);
        select(index, true);
    }

    @NonNull
    private static Fragment fragmentFor(int index) {
        switch (index) {
            case RECITE: return new LibraryFragment();
            case GROUPS: return new GroupsFragment();
            case RANKS: return new RanksFragment();
            case ME: return new MeFragment();
            default: return new HomeFragment();
        }
    }

    private void show(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .setCustomAnimations(R.anim.tab_in, R.anim.tab_out)
                .replace(R.id.nav_host, fragment)
                .commit();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt(STATE_TAB, current);
    }
}
