package com.whispertflite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
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

import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.whispertflite.asr.RecordBuffer;
import com.whispertflite.asr.Recorder;
import com.whispertflite.utils.HapticFeedback;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class DataCollectionActivity extends AppCompatActivity {
    private static final String TAG = "DataCollectionActivity";
    private static final String TASKS_FILE_NAME = "tasks.txt";
    private List<String> mTargetPhrases = new ArrayList<>();
    private int mCurrentIndex = 0;

    private TextView tvTargetText, tvCollectionStatus, tvProgressText, tvPercent;
    private ProgressBar progressBar;
    private ImageButton btnRecord, btnPrev;
    private MaterialButton btnNext, btnPlay, btnImport;
    private TabLayout navigationTabs;

    private Recorder mRecorder;
    private File mCollectionFolder;
    private File mTasksFile;
    private MediaPlayer mMediaPlayer;

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
        StringBuilder content = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(mTasksFile)))) {
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line).append("\n"); // Read all as one raw block first
            }
        } catch (IOException e) {
            Log.e(TAG, "Error reading tasks file", e);
        }

        if (content.length() > 0) {
            // Split by real newlines OR literal \n (backslash n) OR literal /n (forward slash n)
            // Regex handles: 
            // 1. [\r\n]+ (actual system line breaks)
            // 2. \\\\n (literal string "\n")
            // 3. /n (literal string "/n")
            String[] splitTasks = content.toString().split("[\r\n]+|\\\\n|/n");
            for (String task : splitTasks) {
                if (!task.trim().isEmpty()) {
                    mTargetPhrases.add(task.trim());
                }
            }
        }

        if (mTargetPhrases.isEmpty()) {
            mTargetPhrases.add("暂无采集任务，请导入TXT文件");
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
            Toast.makeText(this, "导入成功！", Toast.LENGTH_SHORT).show();
            mCurrentIndex = 0;
            readTasksFromFile();
            updateUI();
        } catch (IOException e) {
            Log.e(TAG, "Error importing file", e);
            Toast.makeText(this, "导入失败", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (navigationTabs != null && navigationTabs.getTabCount() > 1) {
            navigationTabs.getTabAt(1).select();
        }
    }

    private void initViews() {
        tvTargetText = findViewById(R.id.tvTargetText);
        tvCollectionStatus = findViewById(R.id.tvCollectionStatus);
        tvProgressText = findViewById(R.id.tvCollectionProgressText);
        tvPercent = findViewById(R.id.tvCollectionPercent);
        progressBar = findViewById(R.id.collection_progress);

        btnRecord = findViewById(R.id.btnRecordCollection);
        btnPrev = findViewById(R.id.btnPrev);
        btnNext = findViewById(R.id.btnNext);
        btnPlay = findViewById(R.id.btnPlayRecording);
        btnImport = findViewById(R.id.btnImportTxt);

        btnImport.setOnClickListener(v -> {
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
                    finish(); // Go back to MainActivity
                }
            }
            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });

        btnPrev.setOnClickListener(v -> {
            if (mCurrentIndex > 0) {
                mCurrentIndex--;
                updateUI();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (mCurrentIndex < mTargetPhrases.size() - 1) {
                mCurrentIndex++;
                updateUI();
            } else {
                handleCollectionFinished();
            }
        });

        btnPlay.setOnClickListener(v -> playCurrentRecording());
        setupRecordButton();
    }

    private void handleCollectionFinished() {
        boolean allRecorded = true;
        for (int i = 0; i < mTargetPhrases.size(); i++) {
            if (!new File(mCollectionFolder, "phrase_" + i + ".wav").exists()) {
                allRecorded = false;
                break;
            }
        }
        if (allRecorded) {
            Toast.makeText(this, "数据采集完毕！即将开始训练...", Toast.LENGTH_LONG).show();
            new android.os.Handler().postDelayed(this::finish, 1500);
        } else {
            Toast.makeText(this, "请完成所有句子的录制后再结束", Toast.LENGTH_SHORT).show();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupRecordButton() {
        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                HapticFeedback.vibrate(this);
                if (mMediaPlayer != null && mMediaPlayer.isPlaying()) mMediaPlayer.stop();
                startRecording();
                tvCollectionStatus.setText("正在录音...");
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
                    tvCollectionStatus.setText("录音已保存");
                    btnPlay.setVisibility(View.VISIBLE);
                });
            } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                runOnUiThread(() -> tvCollectionStatus.setText("录音失败，请重试"));
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
            btnPlay.setText("正在播放...");
            mMediaPlayer.setOnCompletionListener(mp -> btnPlay.setText("听我的录音"));
        } catch (IOException e) {
            Log.e(TAG, "Playback error", e);
        }
    }

    private void startRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
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
        tvProgressText.setText(String.format("进度：%d / %d", mCurrentIndex + 1, mTargetPhrases.size()));
        int progress = (int) (((float) (mCurrentIndex + 1) / mTargetPhrases.size()) * 100);
        progressBar.setProgress(progress);
        tvPercent.setText(progress + "%");
        btnPrev.setAlpha(mCurrentIndex == 0 ? 0.3f : 1.0f);
        btnPrev.setEnabled(mCurrentIndex > 0);

        if (mCurrentIndex == mTargetPhrases.size() - 1) {
            btnNext.setText("结束");
            btnNext.setIcon(null);
        } else {
            btnNext.setText("");
            btnNext.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_arrow_forward_24dp));
        }

        File audioFile = new File(mCollectionFolder, "phrase_" + mCurrentIndex + ".wav");
        btnPlay.setVisibility(audioFile.exists() ? View.VISIBLE : View.INVISIBLE);
        if (audioFile.exists()) btnPlay.setText("听我的录音");
        
        tvCollectionStatus.setText("点击下方按钮开始录音");
    }

    @Override
    protected void onDestroy() {
        if (mRecorder != null) mRecorder.stop();
        if (mMediaPlayer != null) { mMediaPlayer.release(); mMediaPlayer = null; }
        super.onDestroy();
    }
}