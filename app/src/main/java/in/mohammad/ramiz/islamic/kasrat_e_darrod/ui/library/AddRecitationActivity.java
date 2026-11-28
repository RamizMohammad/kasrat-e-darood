package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.SimpleTextWatcher;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * P2 · Add recitation (screen 14). Super members and admins only (the server
 * checks too). Arabic and an English name are required; the step is how much
 * one tap of "+" adds (1–10,000). Returns RESULT_OK so the Library reloads.
 */
public class AddRecitationActivity extends BaseActivity {

    private TextInputLayout arabicL, nameL, stepL;
    private TextInputEditText arabic, name, urdu, translit, meaning, step;
    private MaterialAutoCompleteTextView category;
    private TextView error;
    private MaterialButton save;
    private View progress;
    private final List<dto.CategoryDto> categories = new ArrayList<>();
    @Nullable private dto.CategoryDto picked;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recitation);
        EdgeInsets.padSystemBars(findViewById(R.id.root), true);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        arabicL = findViewById(R.id.add_arabic_layout);
        nameL = findViewById(R.id.add_name_layout);
        stepL = findViewById(R.id.add_step_layout);
        arabic = findViewById(R.id.add_arabic);
        name = findViewById(R.id.add_name);
        urdu = findViewById(R.id.add_urdu);
        translit = findViewById(R.id.add_translit);
        meaning = findViewById(R.id.add_meaning);
        step = findViewById(R.id.add_step);
        category = findViewById(R.id.add_category);
        error = findViewById(R.id.add_error);
        save = findViewById(R.id.add_save);
        progress = findViewById(R.id.add_progress);

        arabic.addTextChangedListener(new SimpleTextWatcher(t -> arabicL.setError(null)));
        name.addTextChangedListener(new SimpleTextWatcher(t -> nameL.setError(null)));
        step.addTextChangedListener(new SimpleTextWatcher(t -> stepL.setError(null)));
        category.setOnItemClickListener((parent, view, position, id) ->
                picked = position < categories.size() ? categories.get(position) : null);
        save.setOnClickListener(v -> submit());
        loadCategories();
    }

    private void loadCategories() {
        ApiClient.get(this).categories().enqueue(new Callback<List<dto.CategoryDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.CategoryDto>> call,
                                   @NonNull Response<List<dto.CategoryDto>> r) {
                categories.clear();
                if (r.isSuccessful() && r.body() != null) categories.addAll(r.body());
                List<String> names = new ArrayList<>();
                for (dto.CategoryDto c : categories) names.add(c.name != null ? c.name : "—");
                category.setAdapter(new ArrayAdapter<>(AddRecitationActivity.this,
                        android.R.layout.simple_list_item_1, names));
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.CategoryDto>> call, @NonNull Throwable t) { }
        });
    }

    private void submit() {
        error.setVisibility(View.GONE);
        String ar = text(arabic), en = text(name);
        int s = parse(text(step));
        boolean ok = true;
        if (TextUtils.isEmpty(ar)) {
            arabicL.setError(getString(R.string.add_error_arabic));
            ok = false;
        }
        if (en.length() < 2) {
            nameL.setError(getString(R.string.add_error_name));
            ok = false;
        }
        if (s < 1 || s > 10_000) {
            stepL.setError(getString(R.string.add_error_step));
            ok = false;
        }
        if (!ok) {
            Haptics.reject(this);
            return;
        }
        dto.RecitationCreateRequest body = new dto.RecitationCreateRequest();
        body.arabicName = ar;
        body.englishName = en;
        body.urduName = emptyToNull(text(urdu));
        body.transliteration = emptyToNull(text(translit));
        body.translation = emptyToNull(text(meaning));
        body.categoryId = picked != null ? picked.id : null;
        body.defaultIncrement = s;

        setBusy(true);
        ApiClient.get(this).createRecitation(body).enqueue(new Callback<dto.RecitationDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.RecitationDto> call, @NonNull Response<dto.RecitationDto> r) {
                setBusy(false);
                if (r.isSuccessful()) {
                    Haptics.click(AddRecitationActivity.this);
                    setResult(RESULT_OK);
                    finish();
                } else if (r.code() == 403) {
                    fail(getString(R.string.add_error_forbidden));
                } else {
                    String m = ApiErrors.of(r).message;
                    fail(m != null && !m.isEmpty() ? m : getString(R.string.add_error_failed));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.RecitationDto> call, @NonNull Throwable t) {
                setBusy(false);
                fail(getString(R.string.error_network));
            }
        });
    }

    private void fail(@NonNull String message) {
        Haptics.reject(this);
        error.setText(message);
        error.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        save.setEnabled(!busy);
    }

    @NonNull
    private static String text(@NonNull TextView v) {
        return v.getText() == null ? "" : v.getText().toString().trim();
    }

    @Nullable
    private static String emptyToNull(@NonNull String s) {
        return s.isEmpty() ? null : s;
    }

    private static int parse(@NonNull String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
