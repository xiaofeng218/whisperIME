package com.whispertflite;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;

import com.whispertflite.utils.ThemeUtils;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AuthActivity extends AppCompatActivity {
    private static final String TAG = "AuthActivity";
    private static final String SERVER_BASE_URL = "http://43.143.17.185:8000";
    private static final String PREF_USERNAME = "collector_username";

    private EditText etUsername;
    private TextView tvAuthStatus;
    private Button btnLogin;
    private Button btnRegister;
    private ImageButton btnBackMain;

    private SharedPreferences preferences;
    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_auth);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        ThemeUtils.setStatusBarAppearance(this);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        String savedUsername = safeTrim(preferences.getString(PREF_USERNAME, ""));
        if (!savedUsername.isEmpty()) {
            openMainAndFinish();
            return;
        }

        etUsername = findViewById(R.id.etUsername);
        etUsername.setInputType(InputType.TYPE_CLASS_TEXT);
        tvAuthStatus = findViewById(R.id.tvAuthStatus);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnBackMain = findViewById(R.id.btnBackMain);

        btnLogin.setOnClickListener(v -> submitAuth(false));
        btnRegister.setOnClickListener(v -> submitAuth(true));
        btnBackMain.setOnClickListener(v -> openMainAndFinish());
    }

    private void submitAuth(boolean register) {
        String username = safeTrim(etUsername.getText().toString());
        if (username.isEmpty()) {
            Toast.makeText(this, getString(R.string.collection_need_username), Toast.LENGTH_SHORT).show();
            return;
        }

        setAuthLoading(true);
        tvAuthStatus.setText(getString(R.string.collection_connecting));

        networkExecutor.execute(() -> {
            ApiResult result = register ? postAuth("/api/register", username) : postAuth("/api/login", username);
            runOnUiThread(() -> {
                setAuthLoading(false);
                if (result.success) {
                    preferences.edit().putString(PREF_USERNAME, username).apply();
                    Toast.makeText(this, result.message, Toast.LENGTH_SHORT).show();
                    openMainAndFinish();
                } else {
                    tvAuthStatus.setText(result.message);
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private ApiResult postAuth(String path, String username) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(SERVER_BASE_URL + path);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JSONObject body = new JSONObject();
            body.put("username", username);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
                out.write(bytes);
            }

            int code = conn.getResponseCode();
            String raw = readResponse(conn, code >= 200 && code < 300);
            String message = parseMessage(raw,
                    code >= 200 && code < 300
                            ? getString(R.string.collection_success)
                            : getString(R.string.collection_request_failed));
            return new ApiResult(code >= 200 && code < 300, message);
        } catch (Exception e) {
            Log.e(TAG, "postAuth error", e);
            return new ApiResult(false, getString(R.string.collection_network_error, e.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private String readResponse(HttpURLConnection conn, boolean success) throws IOException {
        InputStream stream = success ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private String parseMessage(String raw, String fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        try {
            JSONObject obj = new JSONObject(raw);
            if (obj.has("message")) {
                return obj.optString("message", fallback);
            }
            if (obj.has("error")) {
                return obj.optString("error", fallback);
            }
            return fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private void setAuthLoading(boolean loading) {
        btnLogin.setEnabled(!loading);
        btnRegister.setEnabled(!loading);
        btnBackMain.setEnabled(!loading);
        etUsername.setEnabled(!loading);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private void openMainAndFinish() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }

    private static class ApiResult {
        final boolean success;
        final String message;

        ApiResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }
}
