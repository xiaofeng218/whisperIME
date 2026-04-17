package com.whispertflite.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.whispertflite.BuildConfig;

public final class EndpointConfig {
    private static final String PREF_API_BASE_URL_DEFAULT = "developer_api_base_url_default";
    private static final String PREF_CUSTOM_MODEL_URL_DEFAULT = "developer_custom_model_url_default";
    private static final String PREF_API_BASE_URL_OVERRIDE = "developer_api_base_url_override";
    private static final String PREF_CUSTOM_MODEL_URL_OVERRIDE = "developer_custom_model_url_override";

    private EndpointConfig() {
    }

    public static String getApiBaseUrl(Context context) {
        return getStoredOrDefault(context, PREF_API_BASE_URL_OVERRIDE, getApiDefaultUrl(context));
    }

    public static String getCustomModelUrl(Context context) {
        return getStoredOrDefault(context, PREF_CUSTOM_MODEL_URL_OVERRIDE, getCustomModelDefaultUrl(context));
    }

    public static String getApiDefaultUrl(Context context) {
        return getStoredOrDefault(context, PREF_API_BASE_URL_DEFAULT, BuildConfig.DEFAULT_SERVER_BASE_URL);
    }

    public static String getCustomModelDefaultUrl(Context context) {
        return getStoredOrDefault(context, PREF_CUSTOM_MODEL_URL_DEFAULT, BuildConfig.DEFAULT_CUSTOM_MODEL_URL);
    }

    public static void setApiBaseUrlOverride(Context context, String url) {
        saveOverride(context, PREF_API_BASE_URL_OVERRIDE, url);
    }

    public static void setCustomModelUrlOverride(Context context, String url) {
        saveOverride(context, PREF_CUSTOM_MODEL_URL_OVERRIDE, url);
    }

    public static void setApiBaseUrlDefault(Context context, String url) {
        saveOverride(context, PREF_API_BASE_URL_DEFAULT, url);
    }

    public static void setCustomModelUrlDefault(Context context, String url) {
        saveOverride(context, PREF_CUSTOM_MODEL_URL_DEFAULT, url);
    }

    public static void clearApiBaseUrlOverride(Context context) {
        clearOverride(context, PREF_API_BASE_URL_OVERRIDE);
    }

    public static void clearCustomModelUrlOverride(Context context) {
        clearOverride(context, PREF_CUSTOM_MODEL_URL_OVERRIDE);
    }

    public static boolean isUsingApiOverride(Context context) {
        return hasOverride(context, PREF_API_BASE_URL_OVERRIDE);
    }

    public static boolean isUsingCustomModelOverride(Context context) {
        return hasOverride(context, PREF_CUSTOM_MODEL_URL_OVERRIDE);
    }

    public static String normalizeUrl(String rawValue) {
        if (rawValue == null) {
            return "";
        }
        String value = rawValue.trim();
        if (value.isEmpty()) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (!(value.startsWith("http://") || value.startsWith("https://"))) {
            return "";
        }
        if ("http:".equals(value) || "https:".equals(value)) {
            return "";
        }
        return value;
    }

    private static String getStoredOrDefault(Context context, String key, String defaultValue) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String overrideValue = normalizeUrl(preferences.getString(key, ""));
        if (!overrideValue.isEmpty()) {
            return overrideValue;
        }
        return normalizeUrl(defaultValue);
    }

    private static void saveOverride(Context context, String key, String url) {
        String normalized = normalizeUrl(url);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().putString(key, normalized).apply();
    }

    private static void clearOverride(Context context, String key) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        preferences.edit().remove(key).apply();
    }

    private static boolean hasOverride(Context context, String key) {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String overrideValue = normalizeUrl(preferences.getString(key, ""));
        return !overrideValue.isEmpty();
    }
}
