package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.Cart;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.CatalogAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.SkeletonAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.RecitationIcons;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** E-commerce-style recitation catalog: browse, set counts, review cart, submit. */
public class LibraryFragment extends Fragment implements Cart.Listener {

    private RecyclerView recycler;
    private CatalogAdapter catalog;
    private View cartBar;
    private TextView cartBarText;
    private EditText search;

    /** All catalog items grouped by category (preserves server order). */
    private final Map<String, List<CatalogAdapter.Item>> grouped = new LinkedHashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_library, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recycler = view.findViewById(R.id.recycler_library);
        recycler.setLayoutManager(new LinearLayoutManager(getContext()));
        recycler.setAdapter(new SkeletonAdapter(7));

        cartBar = view.findViewById(R.id.cart_bar);
        cartBarText = view.findViewById(R.id.cart_bar_text);
        cartBar.setOnClickListener(v -> openCart());

        search = view.findViewById(R.id.input_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { rebuild(); }
        });

        load();
    }

    @Override
    public void onResume() {
        super.onResume();
        Cart.get().setListener(this);
        updateCartBar();
    }

    @Override
    public void onCartChanged() { updateCartBar(); }

    private void load() {
        ApiClient.get(requireContext()).recitations(null, null)
                .enqueue(new Callback<List<dto.RecitationDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<dto.RecitationDto>> call,
                                           @NonNull Response<List<dto.RecitationDto>> response) {
                        if (!isAdded()) return;
                        if (response.isSuccessful() && response.body() != null) {
                            ingest(response.body());
                            catalog = new CatalogAdapter();
                            recycler.setAdapter(catalog);
                            rebuild();
                        } else {
                            recycler.setAdapter(new CatalogAdapter());
                            toast(getString(R.string.error_load_failed));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<dto.RecitationDto>> call,
                                          @NonNull Throwable t) {
                        if (!isAdded()) return;
                        recycler.setAdapter(new CatalogAdapter());
                        toast(getString(R.string.error_network));
                    }
                });
    }

    private void ingest(List<dto.RecitationDto> dtos) {
        grouped.clear();
        for (dto.RecitationDto d : dtos) {
            String category = d.category != null ? d.category : "Other";
            String meaning = d.translation != null ? d.translation : "";
            String sub = TextUtils.isEmpty(d.reference) ? meaning
                    : (TextUtils.isEmpty(meaning) ? d.reference : meaning + " · " + d.reference);
            int step = d.defaultIncrement > 0 ? d.defaultIncrement : 1;
            CatalogAdapter.Item item = new CatalogAdapter.Item(
                    d.id,
                    d.urduName != null ? d.urduName : d.arabicName,
                    d.englishName,
                    sub,
                    RecitationIcons.forRecitation(d.icon, d.englishName),
                    step);

            List<CatalogAdapter.Item> list = grouped.get(category);
            if (list == null) {
                list = new ArrayList<>();
                grouped.put(category, list);
            }
            list.add(item);
        }
    }

    /** Builds the row list from the current data + search query, then submits it. */
    private void rebuild() {
        if (catalog == null) return;
        String q = search.getText().toString().trim().toLowerCase(Locale.ROOT);

        List<CatalogAdapter.Row> rows = new ArrayList<>();
        for (Map.Entry<String, List<CatalogAdapter.Item>> e : grouped.entrySet()) {
            List<CatalogAdapter.Item> matches = new ArrayList<>();
            for (CatalogAdapter.Item it : e.getValue()) {
                if (q.isEmpty() || matches(it, q)) matches.add(it);
            }
            if (!matches.isEmpty()) {
                rows.add(CatalogAdapter.Row.header(e.getKey(), matches.size()));
                for (CatalogAdapter.Item it : matches) rows.add(CatalogAdapter.Row.item(it));
            }
        }
        catalog.submit(rows);
    }

    private boolean matches(CatalogAdapter.Item it, String q) {
        return (it.englishName != null && it.englishName.toLowerCase(Locale.ROOT).contains(q))
                || (it.urduName != null && it.urduName.contains(q))
                || (it.sub != null && it.sub.toLowerCase(Locale.ROOT).contains(q));
    }

    private void openCart() {
        if (Cart.get().isEmpty()) return;
        CartBottomSheet sheet = new CartBottomSheet();
        sheet.setOnSubmitted(() -> {
            updateCartBar();
            rebuild();   // reset steppers to reflect cleared/updated cart
        });
        sheet.show(getChildFragmentManager(), "cart");
    }

    private void updateCartBar() {
        if (cartBar == null) return;
        Cart cart = Cart.get();
        if (cart.isEmpty()) {
            cartBar.setVisibility(View.GONE);
        } else {
            cartBar.setVisibility(View.VISIBLE);
            cartBarText.setText(getString(R.string.cart_summary,
                    cart.distinctCount(), cart.totalCount()));
        }
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }
}
