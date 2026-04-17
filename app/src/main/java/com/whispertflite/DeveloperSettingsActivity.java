package com.whispertflite;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import com.whispertflite.utils.EndpointConfig;
import com.whispertflite.utils.ThemeUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeveloperSettingsActivity extends AppCompatActivity {
    private static final String TAG = "DeveloperSettings";

    private TextView tvApiDefaultValue;
    private TextView tvApiEffectiveValue;
    private TextView tvModelDefaultValue;
    private TextView tvModelEffectiveValue;
    private EditText etApiOverride;
    private EditText etModelOverride;

    private final ExecutorService networkExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_developer_settings);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        ThemeUtils.setStatusBarAppearance(this);

        ImageButton btnBack = findViewById(R.id.btnBackDeveloperSettings);
        btnBack.setOnClickListener(v -> finish());

        tvApiDefaultValue = findViewById(R.id.tvApiDefaultValue);
        tvApiEffectiveValue = findViewById(R.id.tvApiEffectiveValue);
        tvModelDefaultValue = findViewById(R.id.tvModelDefaultUrlValue);
        tvModelEffectiveValue = findViewById(R.id.tvModelEffectiveUrlValue);
        etApiOverride = findViewById(R.id.etApiOverride);
        etModelOverride = findViewById(R.id.etModelOverride);

        setupValueToggle(findViewById(R.id.ivApiDefaultArrow), tvApiDefaultValue);
        setupValueToggle(findViewById(R.id.ivApiEffectiveArrow), tvApiEffectiveValue);
        setupValueToggle(findViewById(R.id.ivModelDefaultArrow), tvModelDefaultValue);
        setupValueToggle(findViewById(R.id.ivModelEffectiveArrow), tvModelEffectiveValue);

        Button btnSaveApi = findViewById(R.id.btnSaveApiConfig);
        Button btnResetApi = findViewById(R.id.btnResetApiConfig);
        Button btnTestApi = findViewById(R.id.btnTestApiConfig);
        Button btnSaveModel = findViewById(R.id.btnSaveModelConfig);
        Button btnResetModel = findViewById(R.id.btnResetModelConfig);
        Button btnTestModel = findViewById(R.id.btnTestModelConfig);

        btnSaveApi.setOnClickListener(v -> saveApiOverride());
        btnResetApi.setOnClickListener(v -> {
            saveApiAsDefault();
        });
        btnTestApi.setOnClickListener(v -> testUrl(EndpointConfig.getApiBaseUrl(this), true));

        btnSaveModel.setOnClickListener(v -> saveModelOverride());
        btnResetModel.setOnClickListener(v -> {
            saveModelAsDefault();
        });
        btnTestModel.setOnClickListener(v -> testUrl(EndpointConfig.getCustomModelUrl(this), false));

        refreshUi();
    }

    private void refreshUi() {
        String apiDefaultUrl = EndpointConfig.getApiDefaultUrl(this);
        String apiEffectiveUrl = EndpointConfig.getApiBaseUrl(this);
        String modelDefaultUrl = EndpointConfig.getCustomModelDefaultUrl(this);
        String modelEffectiveUrl = EndpointConfig.getCustomModelUrl(this);

        tvApiDefaultValue.setText(apiDefaultUrl);
        tvApiEffectiveValue.setText(apiEffectiveUrl);

        tvModelDefaultValue.setText(modelDefaultUrl);
        tvModelEffectiveValue.setText(modelEffectiveUrl);

        etApiOverride.setText(EndpointConfig.isUsingApiOverride(this) ? apiEffectiveUrl : "");
        etModelOverride.setText(EndpointConfig.isUsingCustomModelOverride(this) ? modelEffectiveUrl : "");
    }

    private void setupValueToggle(ImageView arrow, TextView valueView) {
        arrow.setOnClickListener(v -> {
            boolean shouldShow = valueView.getVisibility() != View.VISIBLE;
            valueView.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
            arrow.setRotation(shouldShow ? 270f : 90f);
        });
    }

    private void saveApiOverride() {
        String normalized = EndpointConfig.normalizeUrl(etApiOverride.getText().toString());
        if (normalized.isEmpty()) {
            Toast.makeText(this, getString(R.string.dev_settings_invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }
        EndpointConfig.setApiBaseUrlOverride(this, normalized);
        refreshUi();
        Toast.makeText(this, getString(R.string.dev_settings_saved), Toast.LENGTH_SHORT).show();
    }

    private void saveApiAsDefault() {
        String normalized = EndpointConfig.normalizeUrl(etApiOverride.getText().toString());
        if (normalized.isEmpty()) {
            Toast.makeText(this, getString(R.string.dev_settings_invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }
        EndpointConfig.setApiBaseUrlDefault(this, normalized);
        EndpointConfig.setApiBaseUrlOverride(this, normalized);
        refreshUi();
        Toast.makeText(this, getString(R.string.dev_settings_saved_default), Toast.LENGTH_SHORT).show();
    }

    private void saveModelOverride() {
        String normalized = EndpointConfig.normalizeUrl(etModelOverride.getText().toString());
        if (normalized.isEmpty()) {
            Toast.makeText(this, getString(R.string.dev_settings_invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }
        EndpointConfig.setCustomModelUrlOverride(this, normalized);
        refreshUi();
        Toast.makeText(this, getString(R.string.dev_settings_saved), Toast.LENGTH_SHORT).show();
    }

    private void saveModelAsDefault() {
        String normalized = EndpointConfig.normalizeUrl(etModelOverride.getText().toString());
        if (normalized.isEmpty()) {
            Toast.makeText(this, getString(R.string.dev_settings_invalid_url), Toast.LENGTH_SHORT).show();
            return;
        }
        EndpointConfig.setCustomModelUrlDefault(this, normalized);
        EndpointConfig.setCustomModelUrlOverride(this, normalized);
        refreshUi();
        Toast.makeText(this, getString(R.string.dev_settings_saved_default), Toast.LENGTH_SHORT).show();
    }

    private void testUrl(String url, boolean isApi) {
        Toast.makeText(this, getString(R.string.dev_settings_testing), Toast.LENGTH_SHORT).show();
        networkExecutor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String target = isApi ? url : url;
                connection = (HttpURLConnection) new URL(target).openConnection();
                connection.setRequestMethod(isApi ? "GET" : "HEAD");
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                int responseCode = connection.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            getString(R.string.dev_settings_test_success, responseCode),
                            Toast.LENGTH_LONG
                    ).show());
                } else {
                    runOnUiThread(() -> Toast.makeText(
                            this,
                            getString(R.string.dev_settings_test_http_error, responseCode),
                            Toast.LENGTH_LONG
                    ).show());
                }
            } catch (Exception e) {
                Log.e(TAG, "testUrl error", e);
                runOnUiThread(() -> Toast.makeText(
                        this,
                        getString(R.string.dev_settings_test_failed, e.getMessage()),
                        Toast.LENGTH_LONG
                ).show());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @Override
    protected void onDestroy() {
        networkExecutor.shutdownNow();
        super.onDestroy();
    }
}
