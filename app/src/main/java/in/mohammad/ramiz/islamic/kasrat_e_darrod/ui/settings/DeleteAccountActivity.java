package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.core.Haptics;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.base.BaseActivity;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.EdgeInsets;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.util.SimpleTextWatcher;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * P3 · Delete account (screen 32). Step 1 emails a code; step 2 takes the
 * code and deletes. The brick button stays off until a 6-digit code is typed.
 */
public class DeleteAccountActivity extends BaseActivity {

    private static final int CODE_LENGTH = 6;

    private TextInputEditText code;
    private MaterialButton sendCode, confirm;
    private TextView sent, error;
    private boolean deleting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Passwords, codes, account deletion: no screenshots, no tapjacking.
        in.mohammad.ramiz.islamic.kasrat_e_darrod.security.ScreenGuard.protect(this);
        setContentView(R.layout.activity_delete_account);
        EdgeInsets.padSystemBars(findViewById(R.id.root), true);

        code = findViewById(R.id.input_code);
        sendCode = findViewById(R.id.btn_send_code);
        confirm = findViewById(R.id.btn_confirm_delete);
        sent = findViewById(R.id.delete_sent);
        error = findViewById(R.id.delete_error);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.link_cancel).setOnClickListener(v -> finish());
        sendCode.setOnClickListener(v -> requestCode());
        confirm.setOnClickListener(v -> confirmDelete());
        code.addTextChangedListener(new SimpleTextWatcher(t -> {
            error.setVisibility(View.GONE);
            updateConfirm();
        }));
        code.setOnEditorActionListener((v, id, e) -> {
            if (id == EditorInfo.IME_ACTION_DONE && confirm.isEnabled()) {
                confirmDelete();
                return true;
            }
            return false;
        });
    }

    private void updateConfirm() {
        confirm.setEnabled(!deleting && typed().length() == CODE_LENGTH);
    }

    @NonNull
    private String typed() {
        return code.getText() == null ? "" : code.getText().toString().trim();
    }

    private void requestCode() {
        Haptics.click(this);
        showError(0, null);
        sendCode.setEnabled(false);
        sendCode.setText(R.string.sending);
        ApiClient.get(this).requestAccountDeletion().enqueue(new Callback<dto.OkResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.OkResponse> c, @NonNull Response<dto.OkResponse> r) {
                if (isFinishing()) return;
                sendCode.setEnabled(true);
                if (r.isSuccessful()) {
                    sendCode.setText(R.string.delete_resend);
                    sent.setVisibility(View.VISIBLE);
                    code.requestFocus();
                } else {
                    sendCode.setText(R.string.send_deletion_code);
                    showError(0, ApiErrors.messageFrom(r));
                }
            }

            @Override
            public void onFailure(@NonNull Call<dto.OkResponse> c, @NonNull Throwable t) {
                if (isFinishing()) return;
                sendCode.setEnabled(true);
                sendCode.setText(R.string.send_deletion_code);
                showError(R.string.error_network, null);
            }
        });
    }

    private void confirmDelete() {
        String c = typed();
        if (c.length() != CODE_LENGTH || deleting) return;
        Haptics.click(this);
        setDeleting(true);
        ApiClient.get(this).confirmAccountDeletion(new dto.DeleteAccountConfirmRequest(c))
                .enqueue(new Callback<dto.OkResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.OkResponse> call, @NonNull Response<dto.OkResponse> r) {
                        if (isFinishing()) return;
                        if (r.isSuccessful()) {
                            new TokenStore(DeleteAccountActivity.this).clear();
                            Toast.makeText(getApplicationContext(), R.string.account_deleted,
                                    Toast.LENGTH_LONG).show();
                            Intent i = new Intent(DeleteAccountActivity.this, LoginActivity.class)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        } else {
                            setDeleting(false);
                            Haptics.reject(DeleteAccountActivity.this);
                            showError(0, ApiErrors.messageFrom(r));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.OkResponse> call, @NonNull Throwable t) {
                        if (isFinishing()) return;
                        setDeleting(false);
                        showError(R.string.error_network, null);
                    }
                });
    }

    private void setDeleting(boolean on) {
        deleting = on;
        confirm.setText(on ? R.string.deleting : R.string.confirm_delete);
        code.setEnabled(!on);
        sendCode.setEnabled(!on);
        updateConfirm();
    }

    private void showError(@StringRes int res, String text) {
        if (res == 0 && text == null) {
            error.setVisibility(View.GONE);
            return;
        }
        error.setText(res != 0 ? getString(res) : text);
        error.setVisibility(View.VISIBLE);
        error.announceForAccessibility(error.getText());
    }
}
