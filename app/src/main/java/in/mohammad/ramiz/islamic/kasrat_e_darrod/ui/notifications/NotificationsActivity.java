package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.notifications;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.tabs.TabLayout;

import java.util.ArrayList;
import java.util.List;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model.ActivityItem;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.ActivityAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.adapter.LoaderAdapter;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.Anims;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.util.RelativeTime;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Three sections — Activity, Notifications, Considerations — over the feed API. */
public class NotificationsActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {"activity", "notifications", "considerations"};

    private RecyclerView recycler;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        empty = findViewById(R.id.notifications_empty);
        recycler = findViewById(R.id.recycler_notifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));

        TabLayout tabs = findViewById(R.id.tabs);
        tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override public void onTabSelected(TabLayout.Tab tab) {
                load(CATEGORIES[tab.getPosition()]);
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        load(CATEGORIES[0]);   // default to Activity
    }

    private void load(String category) {
        recycler.setAdapter(new LoaderAdapter());
        empty.setVisibility(View.GONE);
        recycler.setVisibility(View.VISIBLE);

        ApiClient.get(this).notifications(category)
                .enqueue(new Callback<List<dto.ActivityDto>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<dto.ActivityDto>> call,
                                           @NonNull Response<List<dto.ActivityDto>> response) {
                        List<dto.ActivityDto> data = response.isSuccessful()
                                && response.body() != null
                                ? response.body() : new ArrayList<>();
                        bind(data);
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<dto.ActivityDto>> call,
                                          @NonNull Throwable t) {
                        bind(new ArrayList<>());
                    }
                });
    }

    private void bind(List<dto.ActivityDto> data) {
        List<ActivityItem> items = new ArrayList<>();
        int i = 0;
        for (dto.ActivityDto a : data) {
            String initial = a.initial != null && !a.initial.isEmpty() ? a.initial : "•";
            String meta = a.type != null ? a.type.replace('_', ' ').replace('.', ' ') : "";
            items.add(new ActivityItem(initial, a.text, meta,
                    RelativeTime.from(a.createdAt), (i++ % 2) == 1));
        }
        recycler.setAdapter(new ActivityAdapter(items));
        Anims.fallDown(recycler);

        boolean isEmpty = items.isEmpty();
        empty.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        recycler.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }
}
