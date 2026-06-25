package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Bottom sheet that reviews the cart and submits it to the community group. */
public class CartBottomSheet extends BottomSheetDialogFragment {

    public interface OnSubmitted { void onSubmitted(); }

    private OnSubmitted callback;
    private MaterialButton submit;
    private CartLineAdapter adapter;

    public void setOnSubmitted(OnSubmitted cb) { this.callback = cb; }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.sheet_cart, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        TextView summary = view.findViewById(R.id.cart_summary);
        TextView empty = view.findViewById(R.id.cart_empty);
        RecyclerView recycler = view.findViewById(R.id.recycler_cart);
        submit = view.findViewById(R.id.btn_submit_cart);

        Cart cart = Cart.get();
        summary.setText(getString(R.string.cart_summary,
                cart.distinctCount(), cart.totalCount()));

        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CartLineAdapter(cart.lines(), line -> {
            Cart.get().remove(line.id);
            refreshAfterRemoval(summary, empty);
        });
        recycler.setAdapter(adapter);

        boolean isEmpty = cart.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        submit.setEnabled(!isEmpty);

        submit.setOnClickListener(v -> doSubmit());
    }

    private void refreshAfterRemoval(TextView summary, TextView empty) {
        Cart cart = Cart.get();
        adapter.setLines(cart.lines());
        summary.setText(getString(R.string.cart_summary,
                cart.distinctCount(), cart.totalCount()));
        boolean isEmpty = cart.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        submit.setEnabled(!isEmpty);
        if (callback != null) callback.onSubmitted();   // keep the catalog/bar in sync
    }

    private void doSubmit() {
        List<dto.BulkItem> items = new ArrayList<>();
        for (Cart.Line l : Cart.get().lines()) {
            items.add(new dto.BulkItem(l.id, l.count, UUID.randomUUID().toString()));
        }
        if (items.isEmpty()) return;

        submit.setEnabled(false);
        submit.setText(R.string.submitting);

        ApiClient.get(requireContext()).submitBulk(new dto.BulkSubmitRequest(items))
                .enqueue(new Callback<dto.Totals>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.Totals> call,
                                           @NonNull Response<dto.Totals> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful()) {
                            Cart.get().clear();
                            Toast.makeText(getContext(), R.string.submit_success,
                                    Toast.LENGTH_LONG).show();
                            if (callback != null) callback.onSubmitted();
                            dismissAllowingStateLoss();
                        } else {
                            submit.setEnabled(true);
                            submit.setText(R.string.submit_to_community);
                            Toast.makeText(getContext(), R.string.error_load_failed,
                                    Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.Totals> call, @NonNull Throwable t) {
                        if (!isAdded()) return;
                        submit.setEnabled(true);
                        submit.setText(R.string.submit_to_community);
                        Toast.makeText(getContext(), R.string.error_network,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
