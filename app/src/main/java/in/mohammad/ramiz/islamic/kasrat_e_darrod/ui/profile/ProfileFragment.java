package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.canhub.cropper.CropImageContract;
import com.canhub.cropper.CropImageContractOptions;
import com.canhub.cropper.CropImageOptions;
import com.canhub.cropper.CropImageView;

import java.io.InputStream;
import java.text.NumberFormat;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.image.ImageApi;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.image.ImageApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.library.AddRecitationActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.settings.SettingsActivity;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Profile: identity, submission stats, and a menu (Settings, Submissions, Dev, Logout). */
public class ProfileFragment extends Fragment {

    private TextView statWeek, statToday, statLifetime, streak;
    private ImageView avatar;
    private View addSurahRow;
    private TokenStore store;

    private final ActivityResultLauncher<CropImageContractOptions> cropper =
            registerForActivityResult(new CropImageContract(), result -> {
                if (result.isSuccessful() && result.getUriContent() != null) {
                    uploadAvatar(result.getUriContent());
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        store = new TokenStore(requireContext());

        TextView name = view.findViewById(R.id.profile_name);
        if (!TextUtils.isEmpty(store.userName())) name.setText(store.userName());

        avatar = view.findViewById(R.id.profile_avatar);
        showAvatar(store.photoUrl());
        view.findViewById(R.id.avatar_container).setOnClickListener(v -> launchCropper());

        statWeek = view.findViewById(R.id.stat_week);
        statToday = view.findViewById(R.id.stat_today);
        statLifetime = view.findViewById(R.id.stat_lifetime);
        streak = view.findViewById(R.id.profile_streak);

        view.findViewById(R.id.row_settings).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SettingsActivity.class)));
        view.findViewById(R.id.row_submissions).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), SubmissionsActivity.class)));
        view.findViewById(R.id.row_week_report).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), WeekReportActivity.class)));

        addSurahRow = view.findViewById(R.id.row_add_surah);
        addSurahRow.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), AddRecitationActivity.class)));
        revealAddSurahForSupers();

        view.findViewById(R.id.row_developer).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), DeveloperInfoActivity.class)));
        view.findViewById(R.id.row_logout).setOnClickListener(v -> {
            store.clear();
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    /** Show the "Add Surah" row only for super members / super admins. */
    private void revealAddSurahForSupers() {
        ApiClient.get(requireContext()).me().enqueue(new Callback<dto.UserDto>() {
            @Override
            public void onResponse(@NonNull Call<dto.UserDto> call,
                                   @NonNull Response<dto.UserDto> response) {
                if (!isAdded() || addSurahRow == null) return;
                String role = response.isSuccessful() && response.body() != null
                        ? response.body().role : null;
                boolean isSuper = "super_member".equals(role) || "super_admin".equals(role);
                addSurahRow.setVisibility(isSuper ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onFailure(@NonNull Call<dto.UserDto> call, @NonNull Throwable t) { }
        });
    }

    private void launchCropper() {
        CropImageOptions opts = new CropImageOptions();
        opts.imageSourceIncludeGallery = true;
        opts.imageSourceIncludeCamera = false;
        opts.cropShape = CropImageView.CropShape.OVAL;
        opts.aspectRatioX = 1;
        opts.aspectRatioY = 1;
        opts.fixAspectRatio = true;
        opts.outputRequestWidth = 600;
        opts.outputRequestHeight = 600;
        cropper.launch(new CropImageContractOptions(null, opts));
    }

    private void showAvatar(String url) {
        if (avatar == null) return;
        avatar.setBackground(null);
        avatar.setImageTintList(null);
        Glide.with(this).load(TextUtils.isEmpty(url) ? null : url)
                .circleCrop()
                .placeholder(R.drawable.default_profile)
                .fallback(R.drawable.default_profile)
                .error(R.drawable.default_profile)
                .into(avatar);
    }

    private void uploadAvatar(Uri uri) {
        byte[] bytes = readBytes(uri);
        if (bytes == null) {
            toast(getString(R.string.photo_failed));
            return;
        }
        toast(getString(R.string.uploading_photo));

        RequestBody body = RequestBody.create(bytes, MediaType.parse("image/*"));
        MultipartBody.Part part = MultipartBody.Part.createFormData("file", "avatar.jpg", body);
        String userId = store.userId() != null ? store.userId() : "anonymous";

        ImageApiClient.get().upload(userId, part).enqueue(new Callback<ImageApi.UploadResponse>() {
            @Override
            public void onResponse(@NonNull Call<ImageApi.UploadResponse> call,
                                   @NonNull Response<ImageApi.UploadResponse> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().image_id != null) {
                    String url = ImageApiClient.imageUrl(response.body().image_id, "large");
                    savePhotoUrl(url);
                } else {
                    toast(getString(R.string.photo_failed));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ImageApi.UploadResponse> call, @NonNull Throwable t) {
                if (isAdded()) toast(getString(R.string.photo_failed));
            }
        });
    }

    private void savePhotoUrl(String url) {
        store.setPhotoUrl(url);
        showAvatar(url);
        toast(getString(R.string.photo_updated));
        // Persist to our backend so it's available everywhere (and in emails).
        ApiClient.get(requireContext()).updateMe(dto.UserUpdateRequest.photo(url))
                .enqueue(new Callback<dto.UserDto>() {
                    @Override public void onResponse(@NonNull Call<dto.UserDto> c,
                                                     @NonNull Response<dto.UserDto> r) {}
                    @Override public void onFailure(@NonNull Call<dto.UserDto> c,
                                                    @NonNull Throwable t) {}
                });
    }

    @Nullable
    private byte[] readBytes(Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) return null;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private void toast(String msg) {
        if (getContext() != null) Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (statWeek != null) loadStats();
    }

    private void loadStats() {
        ApiClient.get(requireContext()).dashboard(null)
                .enqueue(new Callback<dto.DashboardResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.DashboardResponse> call,
                                           @NonNull Response<dto.DashboardResponse> response) {
                        if (!isAdded() || !response.isSuccessful() || response.body() == null) return;
                        dto.DashboardResponse d = response.body();
                        statWeek.setText(format(d.weeklyTotal));
                        statToday.setText(format(d.todayTotal));
                        statLifetime.setText(format(d.lifetimeTotal));
                        streak.setText(getString(R.string.streak_days_label, d.streak));
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.DashboardResponse> call,
                                          @NonNull Throwable t) { /* keep placeholders */ }
                });
    }

    private static String format(int value) {
        return NumberFormat.getIntegerInstance().format(value);
    }
}
