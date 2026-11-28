package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.AuraAvatarView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.RecitationStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.SimpleTextWatcher;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.AppPrefs;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Skeleton;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Recite (Stitch recite_teal): header with streak and avatar, search, category tabs
 * (plus a phone-only Favourites tab), recitation cards, and a floating log bar that
 * sits 12dp above the floating bottom bar. The library itself (screen 10 of P2): Shows the saved list at once, then the server's.
 * Category chips and search filter on the phone. Card chips and steppers fill
 * the cart; the floating bar opens the review sheet.
 */
public class LibraryFragment extends Fragment implements Cart.Listener, RecitationStore.Callback,
        in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity.NavSpaceAware,
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals.Listener {

    /** Tab value for the Favourites filter (categories are the server's names). */
    private static final String FAVOURITES = "\u0000favourites";

    /** Roles allowed to add recitations (server: _LIBRARY_MANAGER_ROLES). */
    private static final Set<String> MANAGERS = Set.of("super_member", "super_admin");
    private static Boolean canManage;   // cached for the session once /auth/me answers

    private RecyclerView list;
    private RecitationCardAdapter adapter;
    private SwipeRefreshLayout refresh;
    private LinearLayout chips;
    private View root;
    private int navSpace;
    private EditText search;
    private View cartBar, skeleton, state, offline;
    private TextView cartBarText;
    private List<dto.RecitationDto> all = new ArrayList<>();
    @Nullable private String category;      // null = All
    private boolean firstFill = true;

    private final ActivityResultLauncher<Intent> addRecitation = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == android.app.Activity.RESULT_OK && isAdded()) {
                    RecitationStore.get(requireContext()).refresh(this);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        root = view;
        String lang = new AppPrefs(requireContext()).lang();
        in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets.padTop(view.findViewById(R.id.library_header));
        bindHeader(view);
        MaterialButton add = view.findViewById(R.id.library_add);
        add.setOnClickListener(v -> addRecitation.launch(new Intent(requireContext(), AddRecitationActivity.class)));

        list = view.findViewById(R.id.library_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new RecitationCardAdapter(lang, r ->
                startActivity(RecitationDetailActivity.intent(requireContext(), r.id)));
        list.setAdapter(adapter);
        adapter.setOnFavourite((r, on) -> {
            if (FAVOURITES.equals(category)) apply();
        });
        refresh = view.findViewById(R.id.library_refresh);
        refresh.setColorSchemeColors(ContextCompat.getColor(requireContext(), R.color.amber));
        refresh.setProgressBackgroundColorSchemeColor(ContextCompat.getColor(requireContext(), R.color.surface_raised));
        refresh.setOnRefreshListener(() -> RecitationStore.get(requireContext()).refresh(this));

        chips = view.findViewById(R.id.library_chips);
        search = view.findViewById(R.id.library_search);
        search.addTextChangedListener(new SimpleTextWatcher(text -> apply()));
        skeleton = view.findViewById(R.id.library_skeleton);
        state = view.findViewById(R.id.library_state);
        offline = view.findViewById(R.id.library_offline);
        cartBar = view.findViewById(R.id.cart_bar);
        cartBarText = view.findViewById(R.id.cart_bar_text);
        cartBar.setOnClickListener(v -> openCart());
        view.findViewById(R.id.cart_bar_log).setOnClickListener(v -> openCart());
        if (getActivity() instanceof in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity) {
            onNavSpace(((in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity) getActivity()).navSpace());
        }

        if (savedInstanceState != null) category = savedInstanceState.getString("category");
        skeleton.setVisibility(View.VISIBLE);
        Skeleton.shimmer(skeleton);
        RecitationStore.get(requireContext()).load(this);
        checkRole(add);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("category", category);
    }

    @Override
    public void onStart() {
        super.onStart();
        Cart.get().addListener(this);
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals.addListener(this);
        onCartChanged();
        if (root != null) bindHeader(root);
        if (adapter != null) adapter.notifyItemRangeChanged(0, adapter.getItemCount());   // hearts
    }

    @Override
    public void onStop() {
        Cart.get().removeListener(this);
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals.removeListener(this);
        super.onStop();
    }

    // ── Header + floating bars ──────────────────────────────────────────────

    private void bindHeader(@NonNull View v) {
        TokenStore store = new TokenStore(requireContext());
        AuraAvatarView avatar = v.findViewById(R.id.library_avatar);
        avatar.setAvatar(store.avatar() != null ? store.avatar() : store.photoUrl());
        avatar.setOnClickListener(x -> openTab(in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity.TAB_ME));
        in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals.Snapshot s =
                in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals.latest();
        showStreak(s != null ? s.streak : 0);
    }

    @Override
    public void onTotals(@NonNull in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Totals.Snapshot s) {
        if (root != null) showStreak(s.streak);
    }

    private void showStreak(long streak) {
        TextView pill = root.findViewById(R.id.library_streak);
        pill.setVisibility(streak > 0 ? View.VISIBLE : View.GONE);
        pill.setText(Numbers.format(streak));
        pill.setContentDescription(getString(R.string.home_streak_cd, (int) streak));
        android.graphics.drawable.Drawable d = ContextCompat.getDrawable(requireContext(), R.drawable.ic_ph_flame);
        if (d != null) {
            d = d.mutate();
            int px = (int) (16 * getResources().getDisplayMetrics().density);
            d.setBounds(0, 0, px, px);
            d.setTint(ContextCompat.getColor(requireContext(), R.color.amber));
            pill.setCompoundDrawablesRelative(d, null, null, null);
        }
    }

    /** The log bar floats 12dp above the bottom bar; the list scrolls clear of both. */
    @Override
    public void onNavSpace(int px) {
        navSpace = px;
        if (cartBar == null || list == null) return;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) cartBar.getLayoutParams();
        lp.bottomMargin = px + (int) (12 * getResources().getDisplayMetrics().density);
        cartBar.setLayoutParams(lp);
        padList();
    }

    private void padList() {
        if (list == null) return;
        float dp = getResources().getDisplayMetrics().density;
        int extra = cartBar != null && cartBar.getVisibility() == View.VISIBLE ? (int) (64 * dp) : 0;
        list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(),
                navSpace + extra + (int) (16 * dp));
    }

    private void openTab(int tab) {
        if (getActivity() instanceof in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity) {
            ((in.mohammad.ramiz.islamic.kasrat_e_darrod.MainActivity) getActivity()).openTab(tab);
        }
    }

    @Override
    public void onCartChanged() {
        if (adapter != null) adapter.cartChanged();
        updateCartBar();
    }

    // ── Data ────────────────────────────────────────────────────────────────

    @Override
    public void onList(@NonNull List<dto.RecitationDto> list, boolean fromCache) {
        if (!isAdded()) return;
        if (!fromCache) {
            refresh.setRefreshing(false);
            offline.setVisibility(View.GONE);
        }
        all = new ArrayList<>(list);
        Skeleton.reveal(skeleton, null);
        buildChips();
        apply();
    }

    @Override
    public void onError(boolean isOffline) {
        if (!isAdded()) return;
        refresh.setRefreshing(false);
        Skeleton.reveal(skeleton, null);
        if (!all.isEmpty()) {
            offline.setVisibility(View.VISIBLE);       // keep showing the saved list
            return;
        }
        showState(isOffline ? R.string.recite_offline_title : R.string.recite_error_title,
                R.string.recite_error_hint, R.string.action_try_again, true, v -> {
                    state.setVisibility(View.GONE);
                    skeleton.setVisibility(View.VISIBLE);
                    Skeleton.shimmer(skeleton);
                    RecitationStore.get(requireContext()).refresh(this);
                });
    }

    private void checkRole(@NonNull MaterialButton add) {
        if (canManage != null) {
            add.setVisibility(canManage ? View.VISIBLE : View.GONE);
            return;
        }
        ApiClient.get(requireContext()).me().enqueue(new Callback<dto.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.UserDto> call, @NonNull Response<dto.UserDto> r) {
                if (r.isSuccessful() && r.body() != null) canManage = MANAGERS.contains(r.body().role);
                if (isAdded() && canManage != null) add.setVisibility(canManage ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) { }
        });
    }

    // ── Filtering ───────────────────────────────────────────────────────────

    private void buildChips() {
        Set<String> cats = new LinkedHashSet<>();
        for (dto.RecitationDto r : all) if (r.category != null && !r.category.isEmpty()) cats.add(r.category);
        if (category != null && !FAVOURITES.equals(category) && !cats.contains(category)) category = null;
        chips.removeAllViews();
        chips.addView(chip(getString(R.string.recite_all), null));
        for (String c : cats) chips.addView(chip(c, c));
        chips.addView(chip(getString(R.string.recite_favourites), FAVOURITES));
        markChips();
    }

    /** Stitch tab: label-sm; chosen = deep-ink capsule (px-4), others plain ink/70 (px-3). */
    @NonNull
    private TextView chip(@NonNull String label, @Nullable String value) {
        TextView c = new TextView(requireContext());
        c.setText(label);
        c.setTag(value);
        c.setTextAppearance(R.style.TextAppearance_K_LabelSm);
        c.setTextColor(ContextCompat.getColorStateList(requireContext(), R.color.sl_k_filter_text));
        c.setBackgroundResource(R.drawable.bg_k_filter_chip);
        c.setGravity(android.view.Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
        c.setLayoutParams(lp);
        c.setOnClickListener(v -> {
            Haptics.tick(requireContext());
            category = (String) v.getTag();
            markChips();
            apply();
            list.scrollToPosition(0);
        });
        return c;
    }

    private void markChips() {
        float dp = getResources().getDisplayMetrics().density;
        for (int i = 0; i < chips.getChildCount(); i++) {
            View c = chips.getChildAt(i);
            Object v = c.getTag();
            boolean on = v == null ? category == null : v.equals(category);
            c.setSelected(on);
            int h = (int) ((on ? 16 : 12) * dp), vpad = (int) (6 * dp);
            c.setPadding(h, vpad, h, vpad);
        }
    }

    private void apply() {
        if (adapter == null) return;
        String q = search.getText() != null ? search.getText().toString().trim().toLowerCase(Locale.ROOT) : "";
        List<dto.RecitationDto> shown = new ArrayList<>();
        java.util.Set<String> favs = FAVOURITES.equals(category)
                ? new in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Favourites(requireContext()).all() : null;
        for (dto.RecitationDto r : all) {
            if (favs != null) {
                if (!favs.contains(r.id)) continue;
            } else if (category != null && !category.equals(r.category)) continue;
            if (!q.isEmpty() && !matches(r, q)) continue;
            shown.add(r);
        }
        adapter.submitList(shown, () -> {
            if (firstFill && !shown.isEmpty()) {
                firstFill = false;
                Motion.enterList(list);
            }
        });
        if (all.isEmpty()) return;
        if (shown.isEmpty()) {
            boolean favTab = favs != null && q.isEmpty();
            showState(favTab ? R.string.recite_no_favourites : R.string.recite_no_results,
                    favTab ? R.string.recite_no_favourites_hint : R.string.recite_no_results_hint,
                    R.string.recite_clear_filters, false, v -> {
                        search.setText("");
                        category = null;
                        markChips();
                        apply();
                    });
            state.findViewById(R.id.library_state_action).setVisibility(favTab ? View.GONE : View.VISIBLE);
        } else {
            state.setVisibility(View.GONE);
        }
    }

    private static boolean matches(@NonNull dto.RecitationDto r, @NonNull String q) {
        return has(r.englishName, q) || has(r.urduName, q) || has(r.arabicName, q)
                || has(r.transliteration, q) || has(r.translation, q) || has(r.category, q);
    }

    private static boolean has(@Nullable String field, @NonNull String q) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(q);
    }

    private void showState(int title, int hint, int action, boolean error, View.OnClickListener onAction) {
        state.setVisibility(View.VISIBLE);
        state.setBackgroundResource(error ? R.drawable.bg_error_plate : 0);
        state.findViewById(R.id.library_state_action).setVisibility(View.VISIBLE);
        ((TextView) state.findViewById(R.id.library_state_title)).setText(title);
        ((TextView) state.findViewById(R.id.library_state_hint)).setText(hint);
        MaterialButton b = state.findViewById(R.id.library_state_action);
        b.setText(action);
        b.setOnClickListener(onAction);
    }

    // ── Cart ────────────────────────────────────────────────────────────────

    private void openCart() {
        if (Cart.get().isEmpty()) return;
        if (getChildFragmentManager().findFragmentByTag("cart") != null) return;
        new CartBottomSheet().show(getChildFragmentManager(), "cart");
    }

    private void updateCartBar() {
        if (cartBar == null) return;
        Cart cart = Cart.get();
        boolean show = !cart.isEmpty();
        if (show) {
            int n = cart.distinctCount();
            String head = getResources().getQuantityString(R.plurals.cart_items, n, n) + " · ";
            String num = Numbers.format(cart.totalCount());
            android.text.SpannableString sp = new android.text.SpannableString(head + num);
            android.graphics.Typeface fr = androidx.core.content.res.ResourcesCompat.getFont(requireContext(), R.font.fraunces);
            if (fr != null) {
                sp.setSpan(new android.text.style.TypefaceSpan(android.graphics.Typeface.create(fr, 700, false)),
                        head.length(), sp.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            cartBarText.setText(sp);
        }
        if (show == (cartBar.getVisibility() == View.VISIBLE)) {
            padList();
            return;
        }
        if (!Motion.enabled()) {
            cartBar.setVisibility(show ? View.VISIBLE : View.GONE);
            padList();
            return;
        }
        float dy = 24 * getResources().getDisplayMetrics().density;
        if (show) {
            cartBar.setAlpha(0f);
            cartBar.setTranslationY(dy);
            cartBar.setVisibility(View.VISIBLE);
            cartBar.animate().alpha(1f).translationY(0f).setDuration(280).withEndAction(null).start();
            padList();
        } else {
            cartBar.animate().alpha(0f).translationY(dy).setDuration(180)
                    .withEndAction(() -> {
                        cartBar.setVisibility(View.GONE);
                        padList();
                    }).start();
        }
    }
}
