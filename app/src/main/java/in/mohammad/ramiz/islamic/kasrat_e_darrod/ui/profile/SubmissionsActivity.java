package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LoaderAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.SubmissionAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Anims;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Full list of the current user's recent submissions. */
public class SubmissionsActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submissions);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        empty = findViewById(R.id.submissions_empty);
        recycler = findViewById(R.id.recycler_submissions);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(new LoaderAdapter());
        load();
    }

    private void load() {
        ApiClient.get(this).mySubmissions().enqueue(new Callback<List<dto.MySubmissionDto>>() {
            @Override
            public void onResponse(@NonNull Call<List<dto.MySubmissionDto>> call,
                                   @NonNull Response<List<dto.MySubmissionDto>> response) {
                List<dto.MySubmissionDto> list = response.isSuccessful() && response.body() != null
                        ? response.body() : new ArrayList<>();
                recycler.setAdapter(new SubmissionAdapter(list));
                Anims.fallDown(recycler);
                setEmpty(list.isEmpty());
            }

            @Override
            public void onFailure(@NonNull Call<List<dto.MySubmissionDto>> call, @NonNull Throwable t) {
                recycler.setAdapter(new SubmissionAdapter(new ArrayList<>()));
                setEmpty(true);
            }
        });
    }

    private void setEmpty(boolean isEmpty) {
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
}
