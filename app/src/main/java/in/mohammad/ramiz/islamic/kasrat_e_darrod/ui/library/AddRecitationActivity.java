package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.KeyboardInsets;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Form for super members / admins to add a new surah / recitation to the library. */
public class AddRecitationActivity extends AppCompatActivity {

    private EditText etEnglish, etArabic, etUrdu, etCount;
    private Spinner spinner;
    private MaterialButton save;
    private View progress;

    private final List<dto.CategoryDto> categories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recitation);
        KeyboardInsets.attach(this);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        etEnglish = findViewById(R.id.et_english);
        etArabic = findViewById(R.id.et_arabic);
        etUrdu = findViewById(R.id.et_urdu);
        etCount = findViewById(R.id.et_count);
        spinner = findViewById(R.id.spinner_category);
        save = findViewById(R.id.btn_save);
        progress = findViewById(R.id.add_progress);

        save.setOnClickListener(v -> submit());
        loadCategories();
    }

    private void loadCategories() {
        ApiClient.get(this).categories().enqueue(new Callback<List<dto.CategoryDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.CategoryDto>> call,
                                   @NonNull Response<List<dto.CategoryDto>> response) {
                categories.clear();
                if (response.isSuccessful() && response.body() != null) {
                    categories.addAll(response.body());
                }
                List<String> names = new ArrayList<>();
                for (dto.CategoryDto c : categories) names.add(c.name != null ? c.name : "—");
                ArrayAdapter<String> a = new ArrayAdapter<>(AddRecitationActivity.this,
                        android.R.layout.simple_spinner_item, names);
                a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinner.setAdapter(a);
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.CategoryDto>> call, @NonNull Throwable t) {
                toast(getString(R.string.error_network));
            }
        });
    }

    private void submit() {
        String english = etEnglish.getText().toString().trim();
        String arabic = etArabic.getText().toString().trim();
        if (TextUtils.isEmpty(english) || TextUtils.isEmpty(arabic)) {
            toast(getString(R.string.add_surah_required));
            return;
        }

        dto.RecitationCreateRequest body = new dto.RecitationCreateRequest();
        body.englishName = english;
        body.arabicName = arabic;
        String urdu = etUrdu.getText().toString().trim();
        body.urduName = TextUtils.isEmpty(urdu) ? null : urdu;

        int pos = spinner.getSelectedItemPosition();
        if (pos >= 0 && pos < categories.size()) body.categoryId = categories.get(pos).id;

        try {
            int c = Integer.parseInt(etCount.getText().toString().trim());
            body.defaultIncrement = Math.max(1, c);
        } catch (NumberFormatException ignored) {
            body.defaultIncrement = 1;
        }

        setBusy(true);
        ApiClient.get(this).createRecitation(body).enqueue(new Callback<dto.RecitationDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.RecitationDto> call,
                                   @NonNull Response<dto.RecitationDto> response) {
                setBusy(false);
                if (response.isSuccessful()) {
                    toast(getString(R.string.add_surah_success));
                    finish();
                } else if (response.code() == 403) {
                    toast(getString(R.string.add_surah_forbidden));
                } else {
                    toast(getString(R.string.add_surah_failed));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.RecitationDto> call, @NonNull Throwable t) {
                setBusy(false);
                toast(getString(R.string.error_network));
            }
        });
    }

    private void setBusy(boolean busy) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        save.setEnabled(!busy);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}
