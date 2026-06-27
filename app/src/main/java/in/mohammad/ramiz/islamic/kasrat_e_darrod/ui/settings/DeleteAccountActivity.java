package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import in.mohammad.ramiz.islamic.kasrat_e_darrod.R;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.local.TokenStore;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiClient;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.ApiErrors;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.dto;
import in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.auth.LoginActivity;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/** Two-step account deletion: email an OTP, then confirm to delete. */
public class DeleteAccountActivity extends AppCompatActivity {

    private EditText code;
    private MaterialButton sendCode, confirm;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_account);

        code = findViewById(R.id.input_code);
        sendCode = findViewById(R.id.btn_send_code);
        confirm = findViewById(R.id.btn_confirm_delete);

        sendCode.setOnClickListener(v -> requestCode());
        confirm.setOnClickListener(v -> confirmDelete());
        findViewById(R.id.link_cancel).setOnClickListener(v -> finish());
    }

    private void requestCode() {
        sendCode.setEnabled(false);
        sendCode.setText(R.string.sending);
        ApiClient.get(this).requestAccountDeletion().enqueue(new Callback<dto.OkResponse>() {
            @Override
            public void onResponse(@NonNull Call<dto.OkResponse> c,
                                   @NonNull Response<dto.OkResponse> r) {
                sendCode.setEnabled(true);
                sendCode.setText(R.string.send_deletion_code);
                Toast.makeText(DeleteAccountActivity.this, R.string.code_sent,
                        Toast.LENGTH_LONG).show();
                code.requestFocus();
            }

            @Override
            public void onFailure(@NonNull Call<dto.OkResponse> c, @NonNull Throwable t) {
                sendCode.setEnabled(true);
                sendCode.setText(R.string.send_deletion_code);
                Toast.makeText(DeleteAccountActivity.this, R.string.error_network,
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDelete() {
        String c = code.getText().toString().trim();
        if (TextUtils.isEmpty(c)) {
            code.setError(getString(R.string.error_code_required));
            return;
        }
        confirm.setEnabled(false);
        confirm.setText(R.string.deleting);
        ApiClient.get(this).confirmAccountDeletion(new dto.DeleteAccountConfirmRequest(c))
                .enqueue(new Callback<dto.OkResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<dto.OkResponse> call,
                                           @NonNull Response<dto.OkResponse> response) {
                        if (response.isSuccessful()) {
                            new TokenStore(DeleteAccountActivity.this).clear();
                            Toast.makeText(DeleteAccountActivity.this,
                                    R.string.account_deleted, Toast.LENGTH_LONG).show();
                            Intent i = new Intent(DeleteAccountActivity.this, LoginActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        } else {
                            confirm.setEnabled(true);
                            confirm.setText(R.string.confirm_delete);
                            Toast.makeText(DeleteAccountActivity.this,
                                    ApiErrors.messageFrom(response), Toast.LENGTH_LONG).show();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<dto.OkResponse> call, @NonNull Throwable t) {
                        confirm.setEnabled(true);
                        confirm.setText(R.string.confirm_delete);
                        Toast.makeText(DeleteAccountActivity.this, R.string.error_network,
                                Toast.LENGTH_LONG).show();
                    }
                });
    }
}
