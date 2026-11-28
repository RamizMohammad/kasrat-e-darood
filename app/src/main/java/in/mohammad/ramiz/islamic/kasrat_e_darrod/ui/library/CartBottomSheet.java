package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec;
import com.google.android.material.progressindicator.IndeterminateDrawable;

import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.LogService;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components.CountRollTextView;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Numbers;

/**
 * Review & log (Stitch review_log_teal): "Ready to log", which circles it counts toward,
 * hairline-split lines with steppers, the Fraunces total and "Log 133 darood". After a
 * log it turns into the success sheet (review_log_success_teal): tick on gold sparkles,
 * "JazakAllah khair", "+133 · Streak 15 days", the circles it went to, and Done.
 *
 * Cart review (P2 screen 13). Steppers edit the cart live; "Log all" sends
 * everything in one request. The server saves the whole batch or nothing, so
 * on failure the cart is kept as it was and "Try again" re-sends the same
 * client_uuids (nothing can count twice). Success: sage tick, then it closes.
 */
public class CartBottomSheet extends BottomSheetDialogFragment implements Cart.Listener {

    private CartLineAdapter adapter;
    private TextView summary, empty, error;
    private CountRollTextView total;
    private MaterialButton logAll;
    private View lines, totalRow, done, body;
    private boolean sending, finished;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        summary = view.findViewById(R.id.cart_summary);
        empty = view.findViewById(R.id.cart_empty);
        error = view.findViewById(R.id.cart_error);
        total = view.findViewById(R.id.cart_total);
        logAll = view.findViewById(R.id.cart_log);
        lines = view.findViewById(R.id.cart_lines);
        totalRow = view.findViewById(R.id.cart_total_row);
        done = view.findViewById(R.id.cart_done);
        body = view.findViewById(R.id.cart_body);
        view.findViewById(R.id.cart_done_btn).setOnClickListener(v -> dismissAllowingStateLoss());

        RecyclerView rv = (RecyclerView) lines;
        rv.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CartLineAdapter();
        rv.setAdapter(adapter);
        logAll.setOnClickListener(v -> send());
        render(false);
    }

    @Override
    public void onStart() {
        super.onStart();
        Cart.get().addListener(this);
    }

    @Override
    public void onStop() {
        Cart.get().removeListener(this);
        super.onStop();
    }

    @Override
    public void onCartChanged() {
        if (!finished && getView() != null) render(true);
    }

    private void render(boolean animate) {
        Cart cart = Cart.get();
        adapter.setLines(cart.lines());
        int n = cart.distinctCount();
        java.util.List<String> circles = in.mohammad.ramiz.islamic.kasrat_e_darrod.data.CircleNames.get();
        if (circles != null && !circles.isEmpty()) {
            summary.setText(getResources().getQuantityString(R.plurals.cart_counts_toward,
                    circles.size(), circles.size()));
        } else {
            summary.setText(getResources().getQuantityString(R.plurals.cart_items, n, n));
        }
        if (!sending) logAll.setText(getString(R.string.cart_log_n, Numbers.format(cart.totalCount())));
        total.setCount(cart.totalCount(), animate && total.hasCount());
        boolean isEmpty = cart.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        totalRow.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        logAll.setEnabled(!isEmpty && !sending);
        if (isEmpty) error.setVisibility(View.GONE);
    }

    private void send() {
        List<Cart.Line> batch = Cart.get().lines();
        if (batch.isEmpty() || sending) return;
        sending = true;
        error.setVisibility(View.GONE);
        logAll.setEnabled(false);
        logAll.setText(R.string.cart_logging);
        logAll.setIcon(spinner());
        LogService.logCart(requireContext(), batch, new LogService.Result() {
            @Override
            public void onLogged(@NonNull dto.Totals totals) {
                sending = false;
                long count = 0;
                for (Cart.Line l : batch) count += l.count;
                Cart.get().removeLogged(batch);
                if (!isAdded()) return;
                showDone(count, totals.streak);
            }

            @Override
            public void onFailed(@NonNull String message, boolean offline) {
                sending = false;
                if (!isAdded()) return;
                Haptics.reject(requireContext());
                logAll.setIcon(null);
                logAll.setText(R.string.action_try_again);
                logAll.setEnabled(!Cart.get().isEmpty());
                error.setText(getString(R.string.cart_failed, message));
                error.setVisibility(View.VISIBLE);
            }
        });
    }

    private void showDone(long count, int streak) {
        finished = true;
        Haptics.click(requireContext());
        body.setVisibility(View.GONE);
        done.setVisibility(View.VISIBLE);
        String days = getResources().getQuantityString(R.plurals.streak_days, Math.max(1, streak), Math.max(1, streak));
        ((TextView) done.findViewById(R.id.cart_done_text)).setText(
                getString(R.string.cart_success_line, Numbers.format(count), days));
        TextView where = done.findViewById(R.id.cart_done_circles);
        java.util.List<String> circles = in.mohammad.ramiz.islamic.kasrat_e_darrod.data.CircleNames.get();
        if (circles != null && !circles.isEmpty()) {
            where.setText(getString(R.string.cart_added_to, joinNames(circles)));
            where.setVisibility(View.VISIBLE);
        } else {
            where.setVisibility(View.GONE);
        }
        View disc = done.findViewById(R.id.cart_done_disc);
        if (in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.Motion.enabled()) {
            disc.setScaleX(0.6f);
            disc.setScaleY(0.6f);
            disc.setAlpha(0f);
            disc.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(280)
                    .setInterpolator(android.view.animation.AnimationUtils.loadInterpolator(
                            requireContext(), R.interpolator.k_surface_enter)).start();
        }
    }

    /** "A", "A & B", "A, B & C". */
    @NonNull
    private String joinNames(@NonNull java.util.List<String> names) {
        if (names.size() == 1) return names.get(0);
        String head = android.text.TextUtils.join(", ", names.subList(0, names.size() - 1));
        return getString(R.string.list_and, head, names.get(names.size() - 1));
    }

    @NonNull
    private IndeterminateDrawable<CircularProgressIndicatorSpec> spinner() {
        CircularProgressIndicatorSpec spec = new CircularProgressIndicatorSpec(requireContext(), null, 0,
                R.style.Widget_Kasrat_Progress_Circular);
        spec.indicatorSize = (int) (18 * getResources().getDisplayMetrics().density);
        spec.trackThickness = (int) (2 * getResources().getDisplayMetrics().density);
        spec.indicatorColors = new int[]{androidx.core.content.ContextCompat.getColor(requireContext(), R.color.on_primary)};
        return IndeterminateDrawable.createCircularDrawable(requireContext(), spec);
    }
}
