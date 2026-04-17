package com.whispertflite.utils;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.io.File;

public final class PublishedModelSync {
    public static final String EXTRA_DOWNLOAD_MODE = "download_mode";
    public static final String EXTRA_PUBLISHED_MODEL_USERNAME = "published_model_username";
    public static final String EXTRA_PUBLISHED_MODEL_VERSION_TAG = "published_model_version_tag";
    public static final String DOWNLOAD_MODE_PUBLISHED_MODEL = "published_model";
    public static final String PREF_PUBLISHED_MODEL_VERSION_PREFIX = "published_model_version_";
    public static final String PUBLISHED_MODEL_FILE_NAME = "whisper-custom.tflite";

    private PublishedModelSync() {
    }

    @NonNull
    public static File getPublishedModelFile(@NonNull Context context) {
        File root = context.getExternalFilesDir(null);
        return new File(root, PUBLISHED_MODEL_FILE_NAME);
    }

    @NonNull
    public static String getDownloadedPublishedVersionTag(
            @NonNull SharedPreferences preferences,
            @NonNull String username
    ) {
        String value = preferences.getString(PREF_PUBLISHED_MODEL_VERSION_PREFIX + username, "");
        return value == null ? "" : value;
    }

    public static void setDownloadedPublishedVersionTag(
            @NonNull SharedPreferences preferences,
            @NonNull String username,
            @NonNull String versionTag
    ) {
        preferences.edit()
                .putString(PREF_PUBLISHED_MODEL_VERSION_PREFIX + username, versionTag)
                .apply();
    }

    public static boolean isPublishedModelVersionInstalled(
            @NonNull Context context,
            @NonNull SharedPreferences preferences,
            @NonNull String username,
            @NonNull String versionTag
    ) {
        if (username.trim().isEmpty() || versionTag.trim().isEmpty()) {
            return false;
        }
        return versionTag.equals(getDownloadedPublishedVersionTag(preferences, username))
                && getPublishedModelFile(context).exists();
    }

    @NonNull
    public static SharedPreferences getPreferences(@NonNull Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
