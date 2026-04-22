package com.whispertflite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.whispertflite.asr.RecordBuffer;
import com.whispertflite.asr.Recorder;
import com.whispertflite.utils.HapticFeedback;
import com.whispertflite.utils.EndpointConfig;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataCollectionActivity extends AppCompatActivity {
    private static final String TAG = "DataCollectionActivity";
    private static final String TASKS_FILE_NAME = "tasks.txt";
    private static final String PREF_USERNAME = "collector_username";

    private List<String> mTargetPhrases = new ArrayList<>();
    private List<Integer> mServerTaskIds = new ArrayList<>();
    private int mCurrentIndex = 0;

    private TextView tvTargetText, tvCollectionStatus, tvProgressText, tvPercent;
    private ProgressBar progressBar, uploadLoading;
    private ImageButton btnRecord, btnPrev;
    private MaterialButton btnNext, btnPlay, btnImport, btnRelogin;
    private TabLayout navigationTabs;

    private Recorder mRecorder;
    private File mCollectionFolder;
    private File mTasksFile;
    private MediaPlayer mMediaPlayer;

    private SharedPreferences mPreferences;
    private String mUsername = "";
    private boolean mIsUploading = false;
    private final ExecutorService mNetworkExecutor = Executors.newSingleThreadExecutor();

    private final ActivityResultLauncher<Intent> mFilePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        importUserTxt(uri);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_data_collection);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        mUsername = safeTrim(mPreferences.getString(PREF_USERNAME, ""));
        if (redirectToAuthIfNeeded()) return;

        mTasksFile = new File(getExternalFilesDir(null), TASKS_FILE_NAME);
        mCollectionFolder = new File(getExternalFilesDir(null), "collection");
        if (!mCollectionFolder.exists()) mCollectionFolder.mkdirs();

        loadTasks();
        initViews();
        setupRecorder();
        updateUI();
    }

    private void loadTasks() {
        if (!mTasksFile.exists()) {
            copyAssetsToInternal();
        }
        readTasksFromFile();
    }

    private void copyAssetsToInternal() {
        try (InputStream is = getAssets().open(TASKS_FILE_NAME);
             FileOutputStream os = new FileOutputStream(mTasksFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error copying assets", e);
        }
    }

    private void readTasksFromFile() {
        mTargetPhrases.clear();
        mServerTaskIds.clear();
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(mTasksFile)))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n");
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading tasks file", e);
        }

        if (content.length() > 0) {
            String[] splitTasks = content.toString().split("[\\r\\n]+|\\\\n|/n");
            for (String task : splitTasks) {
                if (!task.trim().isEmpty()) {
                    mTargetPhrases.add(task.trim());
                }
            }
        }

        if (mTargetPhrases.isEmpty()) {
            mTargetPhrases.add(getString(R.string.collection_no_task));
        }
    }

    private void importUserTxt(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri);
             FileOutputStream os = new FileOutputStream(mTasksFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while (is != null && (length = is.read(buffer)) > 0) {
                os.write(buffer, 0, length);
            }
            Toast.makeText(this, getString(R.string.collection_import_success), Toast.LENGTH_SHORT).show();
            mCurrentIndex = 0;
            readTasksFromFile();
            updateUI();
        } catch (IOException e) {
            Log.e(TAG, "Error importing file", e);
            Toast.makeText(this, getString(R.string.collection_import_failed), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountButtonText();
        if (navigationTabs != null && navigationTabs.getTabCount() > 1) {
            navigationTabs.getTabAt(1).select();
        }
    }

    private void updateAccountButtonText() {
        if (btnRelogin == null) return;
        mUsername = mPreferences.getString(PREF_USERNAME, "");
        if (mUsername == null || mUsername.trim().isEmpty()) {
            btnRelogin.setText(getString(R.string.auth_page_need_login));
        } else {
            btnRelogin.setText(mUsername.trim());
        }
    }

    private void initViews() {
        tvTargetText = findViewById(R.id.tvTargetText);
        tvCollectionStatus = findViewById(R.id.tvCollectionStatus);
        tvProgressText = findViewById(R.id.tvCollectionProgressText);
        tvPercent = findViewById(R.id.tvCollectionPercent);
        progressBar = findViewById(R.id.collection_progress);
        uploadLoading = findViewById(R.id.upload_loading);

        btnRecord = findViewById(R.id.btnRecordCollection);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnPlay = findViewById(R.id.btnPlayRecording);
        btnImport = findViewById(R.id.btnImportTxt);
        btnRelogin = findViewById(R.id.btnRelogin);
        updateAccountButtonText();
        btnRelogin.setOnClickListener(v -> {
            if (!mUsername.isEmpty()) {
                mPreferences.edit().remove(PREF_USERNAME).apply();
            }
            Intent intent = new Intent(this, AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        btnImport.setOnClickListener(v -> {
            if (mIsUploading) return;
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("text/plain");
            mFilePickerLauncher.launch(intent);
        });

        navigationTabs = findViewById(R.id.navigation_tabs);
        navigationTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    if (mRecorder != null) mRecorder.stop();
                    if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                    }
                    finish();
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnPrev.setOnClickListener(v -> {
            if (mIsUploading) return;
            if (mCurrentIndex > 0) {
                mCurrentIndex--;
                updateUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (mIsUploading) return;
            if (mCurrentIndex < mTargetPhrases.size() - 1) {
                mCurrentIndex++;
                updateUI();
            } else {
                startUploadFlow();
            }
        });

        btnPlay.setOnClickListener(v -> playCurrentRecording());
        setupRecordButton();
    }

    private void startUploadFlow() {
        if (!checkAllRecorded()) {
            Toast.makeText(this, getString(R.string.collection_upload_missing_recording), Toast.LENGTH_SHORT).show();
            return;
        }
        if (redirectToAuthIfNeeded()) return;
        setUploadingState(true);
        tvCollectionStatus.setText(getString(R.string.collection_connecting));
        mNetworkExecutor.execute(() -> {
            String baseUrl = EndpointConfig.getApiBaseUrl(this);
            SyncResult syncResult = syncServerTasks(baseUrl, mUsername);
            if (!syncResult.success) {
                runOnUiThread(() -> {
                    setUploadingState(false);
                    Toast.makeText(this, syncResult.message, Toast.LENGTH_LONG).show();
                });
                return;
            }

            UploadSummary summary = uploadAllRecordings(baseUrl, mUsername);
            runOnUiThread(() -> {
                setUploadingState(false);
                String resultMsg = getString(
                        R.string.collection_upload_summary, summary.successCount, summary.failCount);
                tvCollectionStatus.setText(resultMsg);
                Toast.makeText(this, resultMsg, Toast.LENGTH_LONG).show();
                if (summary.failCount == 0) {
                    new android.os.Handler().postDelayed(this::finish, 1500);
                }
            });
        });
    }

    private boolean checkAllRecorded() {
        for (int i = 0; i < mTargetPhrases.size(); i++) {
            if (!new File(mCollectionFolder, "phrase_" + i + ".wav").exists()) {
                return false;
            }
        }
        return true;
    }

    private boolean redirectToAuthIfNeeded() {
        mUsername = safeTrim(mPreferences.getString(PREF_USERNAME, ""));
        if (mUsername.isEmpty()) {
            Intent intent = new Intent(this, AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
            return true;
        }
        return false;
    }

    private SyncResult syncServerTasks(String baseUrl, String username) {
        try {
            JSONArray tasksArray = fetchTasks(baseUrl, username);
            if (tasksArray.length() == 0) {
                ApiResult uploadTxtResult = uploadTxt(baseUrl, username, mTasksFile);
                if (!uploadTxtResult.success) {
                    return new SyncResult(false, uploadTxtResult.message);
                }
                tasksArray = fetchTasks(baseUrl, username);
            }

            List<ServerTask> serverTasks = parseServerTasks(tasksArray);
            mServerTaskIds = buildTaskMapping(serverTasks);
            int validCount = countValidMappings(mServerTaskIds);
            if (validCount == 0) {
                ApiResult clearResult = clearServerTasks(baseUrl, username);
                if (!clearResult.success) {
                    return new SyncResult(false, clearResult.message);
                }

                ApiResult uploadTxtResult = uploadTxt(baseUrl, username, mTasksFile);
                if (!uploadTxtResult.success) {
                    return new SyncResult(false, uploadTxtResult.message);
                }

                JSONArray reloaded = fetchTasks(baseUrl, username);
                List<ServerTask> rebuiltTasks = parseServerTasks(reloaded);
                mServerTaskIds = buildTaskMapping(rebuiltTasks);
                validCount = countValidMappings(mServerTaskIds);
                if (validCount == 0) {
                    return new SyncResult(false, getString(R.string.collection_task_map_failed));
                }
            }
            return new SyncResult(true, getString(R.string.collection_sync_ok));
        } catch (Exception e) {
            Log.e(TAG, "syncServerTasks error", e);
            return new SyncResult(false,
                    getString(R.string.collection_sync_failed, e.getMessage()));
        }
    }

    private int countValidMappings(List<Integer> mappings) {
        int validCount = 0;
        for (int id : mappings) {
            if (id > 0) validCount++;
        }
        return validCount;
    }

    private UploadSummary uploadAllRecordings(String baseUrl, String username) {
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < mTargetPhrases.size(); i++) {
            final int index = i;
            runOnUiThread(() -> tvCollectionStatus.setText(
                    getString(R.string.collection_uploading_progress, index + 1, mTargetPhrases.size())));

            File audioFile = new File(mCollectionFolder, "phrase_" + i + ".wav");
            if (!audioFile.exists()) {
                failCount++;
                continue;
            }
            if (i >= mServerTaskIds.size() || mServerTaskIds.get(i) <= 0) {
                failCount++;
                continue;
            }
            int taskId = mServerTaskIds.get(i);
            ApiResult result = uploadAudio(baseUrl, username, taskId, audioFile);
            if (result.success) successCount++;
            else failCount++;
        }

        return new UploadSummary(successCount, failCount);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupRecordButton() {
        btnRecord.setOnTouchListener((v, event) -> {
            if (mIsUploading) return true;
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                HapticFeedback.vibrate(this);
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) mMediaPlayer.stop();
                startRecording();
                tvCollectionStatus.setText(getString(R.string.collection_recording));
                btnRecord.setScaleX(1.1f);
                btnRecord.setScaleY(1.1f);
                btnPlay.setVisibility(View.INVISIBLE);
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                stopRecording();
                btnRecord.setScaleX(1.0f);
                btnRecord.setScaleY(1.0f);
            }
            return true;
        });
    }

    private void setupRecorder() {
        mRecorder = new Recorder(this);
        mRecorder.setListener(message -> {
            if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                runOnUiThread(() -> {
                    saveCurrentRecording();
                    tvCollectionStatus.setText(getString(R.string.collection_recording_saved));
                    btnPlay.setVisibility(View.VISIBLE);
                });
            } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                runOnUiThread(() -> tvCollectionStatus.setText(
                        getString(R.string.collection_recording_error)));
            }
        });
    }

    private void playCurrentRecording() {
        File audioFile = new File(mCollectionFolder, "phrase_" + mCurrentIndex + ".wav");
        if (!audioFile.exists()) return;
        if (mMediaPlayer != null) mMediaPlayer.release();
        try {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setDataSource(audioFile.getAbsolutePath());
            mMediaPlayer.prepare();
            mMediaPlayer.start();
            btnPlay.setText(getString(R.string.collection_playing));
            mMediaPlayer.setOnCompletionListener(
                    mp -> btnPlay.setText(getString(R.string.play_my_recording)));
        } catch (IOException e) {
            Log.e(TAG, "Playback error", e);
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 0);
            return;
        }
        mRecorder.start();
    }

    private void stopRecording() {
        if (mRecorder != null && mRecorder.isInProgress()) mRecorder.stop();
    }

    private void saveCurrentRecording() {
        byte[] data = RecordBuffer.getOutputBuffer();
        if (data == null || data.length == 0) return;
        File outFile = new File(mCollectionFolder, "phrase_" + mCurrentIndex + ".wav");
        try (FileOutputStream os = new FileOutputStream(outFile)) {
            writeWavHeader(os, data.length);
            os.write(data);
        } catch (IOException e) {
            Log.e(TAG, "Error saving WAV", e);
        }
    }

    private void writeWavHeader(FileOutputStream out, int pcmLen) throws IOException {
        byte[] header = new byte[44];
        long totalDataLen = pcmLen + 36;
        long byteRate = 16000 * 2;
        header[0] = 'R'; header[1] = 'I'; header[2] = 'F'; header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W'; header[9] = 'A'; header[10] = 'V'; header[11] = 'E';
        header[12] = 'f'; header[13] = 'm'; header[14] = 't'; header[15] = ' ';
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0; header[22] = 1; header[23] = 0;
        header[24] = (byte) (16000 & 0xff);
        header[25] = (byte) ((16000 >> 8) & 0xff);
        header[26] = (byte) ((16000 >> 16) & 0xff);
        header[27] = (byte) ((16000 >> 24) & 0xff);
        header[28] = (byte) (byteRate & 0xff);
        header[29] = (byte) ((byteRate >> 8) & 0xff);
        header[30] = (byte) ((byteRate >> 16) & 0xff);
        header[31] = (byte) ((byteRate >> 24) & 0xff);
        header[32] = 2; header[33] = 0; header[34] = 16; header[35] = 0;
        header[36] = 'd'; header[37] = 'a'; header[38] = 't'; header[39] = 'a';
        header[40] = (byte) (pcmLen & 0xff);
        header[41] = (byte) ((pcmLen >> 8) & 0xff);
        header[42] = (byte) ((pcmLen >> 16) & 0xff);
        header[43] = (byte) ((pcmLen >> 24) & 0xff);
        out.write(header, 0, 44);
    }

    private void updateUI() {
        if (mTargetPhrases.isEmpty()) return;
        String phrase = mTargetPhrases.get(mCurrentIndex);
        tvTargetText.setText(phrase);
        tvProgressText.setText(String.format(
                getString(R.string.collection_progress_format),
                mCurrentIndex + 1, mTargetPhrases.size()));
        int progress = (int) (((float) (mCurrentIndex + 1) / mTargetPhrases.size()) * 100);
        progressBar.setProgress(progress);
        tvPercent.setText(progress + "%");
        btnPrev.setAlpha(mCurrentIndex == 0 ? 0.3f : 1.0f);
        btnPrev.setEnabled(mCurrentIndex > 0);

        if (mCurrentIndex == mTargetPhrases.size() - 1) {
            btnNext.setText(getString(R.string.collection_upload));
        } else {
            btnNext.setText("");
        }

        File audioFile = new File(mCollectionFolder, "phrase_" + mCurrentIndex + ".wav");
        btnPlay.setVisibility(audioFile.exists() ? View.VISIBLE : View.INVISIBLE);
        if (audioFile.exists()) btnPlay.setText(getString(R.string.play_my_recording));

        if (!mIsUploading) {
            tvCollectionStatus.setText(getString(R.string.collection_status_ready));
        }
    }

    private void setUploadingState(boolean uploading) {
        mIsUploading = uploading;
        if (uploadLoading != null) {
            uploadLoading.setVisibility(uploading ? View.VISIBLE : View.GONE);
        }
        if (btnRecord != null) btnRecord.setEnabled(!uploading);
        if (btnPrev != null) btnPrev.setEnabled(!uploading && mCurrentIndex > 0);
        if (btnNext != null) btnNext.setEnabled(!uploading);
        if (btnPlay != null) btnPlay.setEnabled(!uploading);
        if (btnImport != null) btnImport.setEnabled(!uploading);
        if (navigationTabs != null) navigationTabs.setEnabled(!uploading);
    }

    private String safeTrim(String value) {
        return value == null ? "" : value.trim();
    }

    private JSONArray fetchTasks(String baseUrl, String username) throws IOException, JSONException {
        String endpoint = baseUrl + "/api/tasks?username=" + URLEncoder.encode(username, "UTF-8");
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            String resp = readResponse(conn, code >= 200 && code < 300);
            if (code < 200 || code >= 300) {
                throw new IOException(parseMessage(resp, getString(R.string.collection_fetch_tasks_failed)));
            }
            return new JSONArray(resp);
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private ApiResult uploadTxt(String baseUrl, String username, File txtFile) {
        if (txtFile == null || !txtFile.exists()) {
            return new ApiResult(false, -1, getString(R.string.collection_missing_tasks_file));
        }
        String endpoint;
        try {
            endpoint = baseUrl + "/api/upload_txt?username=" + URLEncoder.encode(username, "UTF-8");
        } catch (Exception e) {
            return new ApiResult(false, -1, getString(R.string.collection_username_encode_failed));
        }
        return uploadMultipartFile(endpoint, "file", txtFile, "text/plain");
    }

    private ApiResult uploadAudio(String baseUrl, String username, int taskId, File audioFile) {
        String endpoint;
        try {
            endpoint = baseUrl + "/api/upload_audio/" + taskId + "?username="
                    + URLEncoder.encode(username, "UTF-8");
        } catch (Exception e) {
            return new ApiResult(false, -1, getString(R.string.collection_username_encode_failed));
        }
        return uploadMultipartFile(endpoint, "audio", audioFile, "audio/wav");
    }

    private ApiResult clearServerTasks(String baseUrl, String username) {
        HttpURLConnection conn = null;
        try {
            String endpoint = baseUrl + "/api/tasks?username=" + URLEncoder.encode(username, "UTF-8");
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("DELETE");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();
            String resp = readResponse(conn, code >= 200 && code < 300);
            String message = parseMessage(resp, getString(R.string.collection_request_failed));
            return new ApiResult(code >= 200 && code < 300, code, message);
        } catch (Exception e) {
            Log.e(TAG, "clearServerTasks error", e);
            return new ApiResult(false, -1,
                    getString(R.string.collection_network_error, e.getMessage()));
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private ApiResult uploadMultipartFile(String endpoint, String fieldName, File file, String contentType) {
        HttpURLConnection conn = null;
        String boundary = "----WhisperIMEBoundary" + UUID.randomUUID();
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            try (DataOutputStream out = new DataOutputStream(conn.getOutputStream());
                 FileInputStream fis = new FileInputStream(file)) {
                out.writeBytes("--" + boundary + "\r\n");
                out.writeBytes("Content-Disposition: form-data; name=\"" + fieldName
                        + "\"; filename=\"" + file.getName() + "\"\r\n");
                out.writeBytes("Content-Type: " + contentType + "\r\n\r\n");

                byte[] buffer = new byte[4096];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.writeBytes("\r\n");
                out.writeBytes("--" + boundary + "--\r\n");
                out.flush();
            }

            int code = conn.getResponseCode();
            String resp = readResponse(conn, code >= 200 && code < 300);
            String message = parseMessage(resp, code >= 200 && code < 300
                    ? getString(R.string.collection_upload_success)
                    : getString(R.string.collection_upload_failed));
            return new ApiResult(code >= 200 && code < 300, code, message);
        } catch (Exception e) {
            Log.e(TAG, "uploadMultipartFile error", e);
            return new ApiResult(false, -1,
                    getString(R.string.collection_network_error, e.getMessage()));
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
        } catch (JSONException e) {
            return fallback;
        }
    }

    private List<ServerTask> parseServerTasks(JSONArray array) throws JSONException {
        List<ServerTask> tasks = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject obj = array.getJSONObject(i);
            int id = obj.optInt("id", -1);
            String text = safeTrim(obj.optString("text_content", ""));
            if (id > 0 && !text.isEmpty()) {
                tasks.add(new ServerTask(id, text));
            }
        }
        tasks.sort(Comparator.comparingInt(task -> task.id));
        return tasks;
    }

    private List<Integer> buildTaskMapping(List<ServerTask> serverTasks) {
        Map<String, ArrayDeque<Integer>> taskBuckets = new HashMap<>();
        for (ServerTask task : serverTasks) {
            taskBuckets.computeIfAbsent(task.textContent, k -> new ArrayDeque<>()).add(task.id);
        }

        List<Integer> mapping = new ArrayList<>();
        for (String phrase : mTargetPhrases) {
            ArrayDeque<Integer> ids = taskBuckets.get(safeTrim(phrase));
            if (ids != null && !ids.isEmpty()) {
                mapping.add(ids.poll());
            } else {
                mapping.add(-1);
            }
        }
        return mapping;
    }

    @Override
    protected void onDestroy() {
        if (mRecorder != null) mRecorder.stop();
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
        mNetworkExecutor.shutdownNow();
        super.onDestroy();
    }

    private static class ServerTask {
        final int id;
        final String textContent;

        ServerTask(int id, String textContent) {
            this.id = id;
            this.textContent = textContent;
        }
    }

    private static class ApiResult {
        final boolean success;
        final int code;
        final String message;

        ApiResult(boolean success, int code, String message) {
            this.success = success;
            this.code = code;
            this.message = message;
        }
    }

    private static class SyncResult {
        final boolean success;
        final String message;

        SyncResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private static class UploadSummary {
        final int successCount;
        final int failCount;

        UploadSummary(int successCount, int failCount) {
            this.successCount = successCount;
            this.failCount = failCount;
        }
    }
}
