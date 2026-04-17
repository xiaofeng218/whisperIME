package com.whispertflite;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.EditText;
import androidx.activity.OnBackPressedCallback;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.github.houbb.opencc4j.util.ZhConverterUtil;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.whispertflite.asr.Recorder;
import com.whispertflite.asr.Whisper;
import com.whispertflite.asr.WhisperResult;
import com.whispertflite.utils.EndpointConfig;
import com.whispertflite.utils.HapticFeedback;
import com.whispertflite.utils.InputLang;
import com.whispertflite.utils.ModelSelection;
import com.whispertflite.utils.PublishedModelSync;
import com.whispertflite.utils.ThemeUtils;

import org.json.JSONObject;
import org.woheller69.freeDroidWarn.FreeDroidWarn;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private Context mContext;
    private static final String TAG = "MainActivity";
    private static final String PREF_USERNAME = "collector_username";
    private static final long MODEL_UPDATE_POLL_INTERVAL_MS = 120000L;

    public static final String MULTI_LINGUAL_EU_MODEL_FAST = "whisper-base.EUROPEAN_UNION.tflite";
    public static final String MULTI_LINGUAL_TOP_WORLD_FAST = "whisper-base.TOP_WORLD.tflite";
    public static final String MULTI_LINGUAL_TOP_WORLD_SLOW = "whisper-small.TOP_WORLD.tflite";
    public static final String MULTI_LINGUAL_MODEL_FAST = "whisper-base.tflite";
    public static final String MULTI_LINGUAL_MODEL_SLOW = "whisper-small.tflite";
    public static final String ENGLISH_ONLY_MODEL = "whisper-tiny.en.tflite";
    public static final String CUSTOM_MODEL = "whisper-small.tflite";
    public static final String ENGLISH_ONLY_MODEL_EXTENSION = ".en.tflite";
    public static final String ENGLISH_ONLY_VOCAB_FILE = "filters_vocab_en.bin";
    public static final String MULTILINGUAL_VOCAB_FILE = "filters_vocab_multilingual.bin";

    private TextView tvStatus;
    private EditText tvResult;
    private FloatingActionButton fabCopy;
    private MaterialButton btnModelUpdate;
    private ImageButton btnRecord;
    private ImageView ivModelUpdateFlame;
    private LinearLayout layoutModeChinese;
    private LinearLayout layoutTTS;
    private CheckBox append;
    private CheckBox translate;
    private CheckBox modeSimpleChinese;
    private CheckBox modeTTS;
    private ProgressBar processingBar;
    private MaterialButton btnRelogin;
    private TabLayout navigationTabs;

    private Recorder mRecorder = null;
    private Whisper mWhisper = null;

    private File sdcardDataFolder = null;
    private File selectedTfliteFile = null;
    private SharedPreferences sp = null;
    private Spinner spinnerTflite;
    private CountDownTimer countDownTimer;
    private Spinner spinnerLanguage;
    private int langToken = -1;
    private long startTime = 0;
    private TextToSpeech tts;
    private final Handler modelUpdateHandler = new Handler(Looper.getMainLooper());
    private boolean isCheckingModelUpdate = false;
    private String availablePublishedVersionTag = "";
    private final Runnable modelUpdatePollRunnable = this::checkForPublishedModelUpdate;

    @Override
    protected void onDestroy() {
        modelUpdateHandler.removeCallbacks(modelUpdatePollRunnable);
        deinitModel();
        deinitTTS();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        modelUpdateHandler.removeCallbacks(modelUpdatePollRunnable);
        stopProcessing();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateAccountButtonText();
        if (spinnerTflite != null && sdcardDataFolder != null) {
            refreshModelSpinner(false);
        }
        startModelUpdateMonitoring();
        if (navigationTabs != null && navigationTabs.getTabCount() > 0) {
            navigationTabs.getTabAt(0).select();
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;
        setContentView(R.layout.activity_main);
        ThemeUtils.setStatusBarAppearance(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        checkInputMethodEnabledOncePerProcess();
        processingBar = findViewById(R.id.processing_bar);
        sp = PreferenceManager.getDefaultSharedPreferences(this);
        append = findViewById(R.id.mode_append);

        navigationTabs = findViewById(R.id.navigation_tabs);
        if (navigationTabs != null) {
            navigationTabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
                @Override
                public void onTabSelected(TabLayout.Tab tab) {
                    if (tab.getPosition() == 1) {
                        if (!isLoggedIn()) {
                            Toast.makeText(
                                    MainActivity.this,
                                    getString(R.string.collection_login_required),
                                    Toast.LENGTH_SHORT
                            ).show();
                            if (navigationTabs.getTabCount() > 0) {
                                navigationTabs.getTabAt(0).select();
                            }
                            Intent authIntent = new Intent(MainActivity.this, AuthActivity.class);
                            authIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(authIntent);
                            return;
                        }
                        Intent intent = new Intent(MainActivity.this, DataCollectionActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                    }
                }
                @Override public void onTabUnselected(TabLayout.Tab tab) {}
                @Override public void onTabReselected(TabLayout.Tab tab) {}
            });
        }

        layoutTTS = findViewById(R.id.layout_tts);
        modeTTS = findViewById(R.id.mode_tts);
        modeTTS.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            if (isChecked) {
                tts = new TextToSpeech(mContext, status -> {
                    if (status == TextToSpeech.SUCCESS) {
                        int result = tts.setLanguage(Locale.US);
                        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            runOnUiThread(() -> {
                                Toast.makeText(mContext, mContext.getString(R.string.tts_language_not_supported),Toast.LENGTH_SHORT).show();
                                modeTTS.setChecked(false);
                            });
                        }
                    } else {
                        runOnUiThread(() -> Toast.makeText(mContext, mContext.getString(R.string.tts_initialization_failed),Toast.LENGTH_SHORT).show());
                    }
                });
            } else {
                deinitTTS();
            }
        });

        translate = findViewById(R.id.mode_translate);
        translate.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            layoutTTS.setVisibility(isChecked ? View.VISIBLE:View.GONE);
            if (layoutTTS.getVisibility() == View.GONE) modeTTS.setChecked(false);
        });

        sdcardDataFolder = this.getExternalFilesDir(null);

        btnRelogin = findViewById(R.id.btnRelogin);
        updateAccountButtonText();
        btnRelogin.setOnClickListener(view -> {
            if (isLoggedIn()) {
                SharedPreferences.Editor editor = sp.edit();
                editor.remove(PREF_USERNAME);
                editor.apply();
            }
            Intent intent = new Intent(MainActivity.this, AuthActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        spinnerLanguage = findViewById(R.id.spnrLanguage);
        langToken = InputLang.getIdForLanguage(InputLang.getLangList(), "zh");
        
        ArrayList<File> tfliteFiles = getFilesWithExtension(sdcardDataFolder, ".tflite");
        File preferredModel = new File(sdcardDataFolder, sp.getString("modelName", MULTI_LINGUAL_MODEL_SLOW));
        selectedTfliteFile = ModelSelection.resolveSelectedModel(preferredModel, tfliteFiles);
        ArrayAdapter<File> tfliteAdapter = getFileArrayAdapter(tfliteFiles);
        spinnerTflite = findViewById(R.id.spnrTfliteFiles);
        spinnerTflite.setAdapter(tfliteAdapter);
        if (selectedTfliteFile != null) {
            int position = tfliteAdapter.getPosition(selectedTfliteFile);
            spinnerTflite.setSelection(Math.max(position, 0), false);
            SharedPreferences.Editor editor = sp.edit();
            editor.putString("modelName", selectedTfliteFile.getName());
            editor.apply();
        } else {
            Toast.makeText(this, getString(R.string.error_no_models_available), Toast.LENGTH_SHORT).show();
            spinnerTflite.setEnabled(false);
        }
        
        spinnerTflite.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                deinitModel();
                selectedTfliteFile = (File) parent.getItemAtPosition(position);
                SharedPreferences.Editor editor = sp.edit();
                editor.putString("modelName",selectedTfliteFile.getName());
                editor.apply();
                initModel();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnRecord = findViewById(R.id.btnRecord);
        btnRecord.setEnabled(selectedTfliteFile != null);
        btnRecord.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                if (mWhisper == null) {
                    Toast.makeText(this, getString(R.string.error_no_models_available), Toast.LENGTH_SHORT).show();
                    return true;
                }
                if (!mWhisper.isInProgress()) {
                    HapticFeedback.vibrate(this);
                    startRecording();
                    runOnUiThread(() -> processingBar.setProgress(100));
                    countDownTimer = new CountDownTimer(30000, 1000) {
                        @Override
                        public void onTick(long l) {
                            runOnUiThread(() -> processingBar.setProgress((int) (l / 300)));
                        }
                        @Override public void onFinish() {}
                    };
                    countDownTimer.start();
                } else (Toast.makeText(this,getString(R.string.please_wait),Toast.LENGTH_SHORT)).show();
            } else if (event.getAction() == MotionEvent.ACTION_UP) {
                runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                if (mRecorder != null && mRecorder.isInProgress()) {
                    stopRecording();
                }
            }
            return true;
        });
        initModel();

        layoutModeChinese = findViewById(R.id.layout_mode_chinese);
        modeSimpleChinese = findViewById(R.id.mode_simple_chinese);
        modeSimpleChinese.setChecked(sp.getBoolean("simpleChinese",false));
        modeSimpleChinese.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            SharedPreferences.Editor editor = sp.edit();
            editor.putBoolean("simpleChinese", isChecked);
            editor.apply();
            tvResult.setText("");
        });

        tvStatus = findViewById(R.id.tvStatus);
        tvResult = findViewById(R.id.tvResult);
        tvResult.setOnClickListener(view -> tvResult.setCursorVisible(true));
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (tvResult.isCursorVisible()) tvResult.setCursorVisible(false);
                else finish();
            }
        });
        fabCopy = findViewById(R.id.fabCopy);
        fabCopy.setOnClickListener(v -> {
            String textToCopy = tvResult.getText().toString().trim();
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(getString(R.string.model_output), textToCopy);
            clipboard.setPrimaryClip(clip);
        });
        btnModelUpdate = findViewById(R.id.btnModelUpdate);
        ivModelUpdateFlame = findViewById(R.id.ivModelUpdateFlame);
        btnModelUpdate.setOnClickListener(v -> onModelUpdateClicked());
        updateModelUpdateUi(false);

        mRecorder = new Recorder(this);
        mRecorder.setListener(new Recorder.RecorderListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.equals(Recorder.MSG_RECORDING)) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.record_button) +"…"));
                    if (!append.isChecked()) runOnUiThread(() -> tvResult.setText(""));
                    runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background_pressed));
                } else if (message.equals(Recorder.MSG_RECORDING_DONE)) {
                    HapticFeedback.vibrate(mContext);
                    runOnUiThread(() -> btnRecord.setBackgroundResource(R.drawable.rounded_button_background));
                    if (translate.isChecked()) startProcessing(Whisper.ACTION_TRANSLATE);
                    else startProcessing(Whisper.ACTION_TRANSCRIBE);
                } else if (message.equals(Recorder.MSG_RECORDING_ERROR)) {
                    HapticFeedback.vibrate(mContext);
                    if (countDownTimer!=null) { countDownTimer.cancel();}
                    runOnUiThread(() -> {
                        btnRecord.setBackgroundResource(R.drawable.rounded_button_background);
                        processingBar.setProgress(0);
                        tvStatus.setText(getString(R.string.error_no_input));
                    });
                }
            }
        });
        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);
        if (GithubStar.shouldShowStarDialog(this)) GithubStar.starDialog(this, "https://github.com/woheller69/whisperIME");
        checkPermissions();
    }

    private void checkInputMethodEnabledOncePerProcess() {
        if (!InputMethodPromptGate.consumeShouldPrompt()) {
            return;
        }
        checkInputMethodEnabled();
    }

    private void checkInputMethodEnabled() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        List<InputMethodInfo> enabledInputMethodList = imm.getEnabledInputMethodList();
        String myInputMethodId = getPackageName() + "/" + WhisperInputMethodService.class.getName();
        boolean inputMethodEnabled = false;
        for (InputMethodInfo imi : enabledInputMethodList) {
            if (imi.getId().equals(myInputMethodId)) {
                inputMethodEnabled = true;
                break;
            }
        }
        if (!inputMethodEnabled) {
            Intent intent = new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
        }
    }

    private boolean isLoggedIn() {
        String username = sp.getString(PREF_USERNAME, "");
        return username != null && !username.trim().isEmpty();
    }

    private void updateAccountButtonText() {
        if (btnRelogin == null || sp == null) {
            return;
        }
        String username = sp.getString(PREF_USERNAME, "");
        if (username == null || username.trim().isEmpty()) {
            btnRelogin.setText(getString(R.string.auth_page_need_login));
        } else {
            btnRelogin.setText(username.trim());
        }
    }

    private void initModel() {
        if (selectedTfliteFile == null || !selectedTfliteFile.exists()) {
            Log.e(TAG, "No valid model available for initialization");
            if (tvStatus != null) {
                runOnUiThread(() -> tvStatus.setText(getString(R.string.error_no_models_available)));
            }
            return;
        }
        File modelFile = selectedTfliteFile;
        boolean isMultilingualModel = !(modelFile.getName().endsWith(ENGLISH_ONLY_MODEL_EXTENSION));
        String vocabFileName = isMultilingualModel ? MULTILINGUAL_VOCAB_FILE : ENGLISH_ONLY_VOCAB_FILE;
        File vocabFile = new File(sdcardDataFolder, vocabFileName);

        mWhisper = new Whisper(this);
        mWhisper.loadModel(modelFile, vocabFile, isMultilingualModel);
        mWhisper.setListener(new Whisper.WhisperListener() {
            @Override
            public void onUpdateReceived(String message) {
                if (message.equals(Whisper.MSG_PROCESSING)) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.processing)));
                    startTime = System.currentTimeMillis();
                    runOnUiThread(() -> spinnerTflite.setEnabled(false));
                }
            }

            @Override
            public void onResultReceived(WhisperResult whisperResult) {
                long timeTaken = System.currentTimeMillis() - startTime;
                runOnUiThread(() -> tvStatus.setText(getString(R.string.processing_done) + timeTaken + "\u2009ms" + "\n"+ getString(R.string.language) + " " + new Locale(whisperResult.getLanguage()).getDisplayLanguage() + " " + (whisperResult.getTask() == Whisper.Action.TRANSCRIBE ? getString(R.string.mode_transcription) : getString(R.string.mode_translation))));
                runOnUiThread(() -> processingBar.setIndeterminate(false));
                
                runOnUiThread(() -> layoutModeChinese.setVisibility(View.GONE));
                String result = ZhConverterUtil.toSimple(whisperResult.getResult());
                runOnUiThread(() -> tvResult.append(result));
                
                runOnUiThread(() -> spinnerTflite.setEnabled(true));
                if (modeTTS.isChecked()){
                    tts.speak(whisperResult.getResult(), TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        });
    }

    private void deinitModel() {
        if (mWhisper != null) {
            mWhisper.unloadModel();
            mWhisper = null;
        }
    }

    private void deinitTTS(){
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    private @NonNull ArrayAdapter<File> getFileArrayAdapter(ArrayList<File> tfliteFiles) {
        ArrayAdapter<File> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, tfliteFiles) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                String fileName = getItem(position).getName();
                if (fileName.equals(MULTI_LINGUAL_MODEL_SLOW)) textView.setText(R.string.multi_lingual_default);
                else if (fileName.equals(PublishedModelSync.PUBLISHED_MODEL_FILE_NAME)) textView.setText(R.string.custom_model);
                else if (fileName.equals(MULTI_LINGUAL_TOP_WORLD_SLOW)) textView.setText(R.string.multi_lingual_slow);
                else if (fileName.equals(ENGLISH_ONLY_MODEL)) textView.setText(R.string.english_only_fast);
                else if (fileName.equals(MULTI_LINGUAL_MODEL_FAST)) textView.setText(R.string.multi_lingual_fast);
                else if (fileName.equals(MULTI_LINGUAL_EU_MODEL_FAST)) textView.setText(R.string.multi_lingual_fast);
                else if (fileName.equals(MULTI_LINGUAL_TOP_WORLD_FAST)) textView.setText(R.string.multi_lingual_fast);
                else textView.setText(fileName.substring(0, fileName.length() - ".tflite".length()));
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView textView = view.findViewById(android.R.id.text1);
                String fileName = getItem(position).getName();
                if (fileName.equals(MULTI_LINGUAL_MODEL_SLOW)) textView.setText(R.string.multi_lingual_default);
                else if (fileName.equals(PublishedModelSync.PUBLISHED_MODEL_FILE_NAME)) textView.setText(R.string.custom_model);
                else if (fileName.equals(MULTI_LINGUAL_TOP_WORLD_SLOW)) textView.setText(R.string.multi_lingual_slow);
                else if (fileName.equals(ENGLISH_ONLY_MODEL)) textView.setText(R.string.english_only_fast);
                else if (fileName.equals(MULTI_LINGUAL_MODEL_FAST)) textView.setText(R.string.multi_lingual_fast);
                else if (fileName.equals(MULTI_LINGUAL_EU_MODEL_FAST)) textView.setText(R.string.multi_lingual_fast);
                else if (fileName.equals(MULTI_LINGUAL_TOP_WORLD_FAST)) textView.setText(R.string.multi_lingual_fast);
                else textView.setText(fileName.substring(0, fileName.length() - ".tflite".length()));
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void checkPermissions() {
        List<String> perms = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            perms.add(Manifest.permission.RECORD_AUDIO);
            Toast.makeText(this, getString(R.string.need_record_audio_permission), Toast.LENGTH_SHORT).show();
        }
        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)){
            perms.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (!perms.isEmpty()) {
            requestPermissions(perms.toArray(new String[] {}), 0);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Record permission is granted");
        }
    }

    private void startRecording() {
        checkPermissions();
        mRecorder.start();
    }

    private void stopRecording() {
        mRecorder.stop();
    }

    private void startProcessing(Whisper.Action action) {
        if (countDownTimer!=null) { countDownTimer.cancel();}
        runOnUiThread(() -> {
            processingBar.setProgress(0);
            processingBar.setIndeterminate(true);
        });
        mWhisper.setAction(Whisper.Action.TRANSCRIBE);
        mWhisper.setLanguage(langToken);
        mWhisper.start();
    }

    private void stopProcessing() {
        runOnUiThread(() -> processingBar.setIndeterminate(false));
        if (mWhisper != null && mWhisper.isInProgress()) mWhisper.stop();
    }

    public ArrayList<File> getFilesWithExtension(File directory, String extension) {
        ArrayList<File> filteredFiles = new ArrayList<>();
        if (directory != null && directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(extension)) {
                        filteredFiles.add(file);
                    }
                }
            }
        }
        return filteredFiles;
    }

    private void startModelUpdateMonitoring() {
        modelUpdateHandler.removeCallbacks(modelUpdatePollRunnable);
        if (isLoggedIn()) {
            modelUpdateHandler.post(modelUpdatePollRunnable);
        } else {
            availablePublishedVersionTag = "";
            updateModelUpdateUi(false);
        }
    }

    private void scheduleNextModelUpdateCheck() {
        if (!isFinishing() && !isDestroyed() && isLoggedIn()) {
            modelUpdateHandler.removeCallbacks(modelUpdatePollRunnable);
            modelUpdateHandler.postDelayed(modelUpdatePollRunnable, MODEL_UPDATE_POLL_INTERVAL_MS);
        }
    }

    private void checkForPublishedModelUpdate() {
        if (isCheckingModelUpdate || !isLoggedIn()) {
            scheduleNextModelUpdateCheck();
            return;
        }
        String username = getLoggedInUsername();
        if (username.isEmpty()) {
            updateModelUpdateUi(false);
            scheduleNextModelUpdateCheck();
            return;
        }
        isCheckingModelUpdate = true;
        new Thread(() -> {
            try {
                String url = EndpointConfig.getApiBaseUrl(this) + "/api/latest_model_info?username="
                        + URLEncoder.encode(username, StandardCharsets.UTF_8.name());
                HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
                try {
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(6000);
                    connection.setReadTimeout(6000);
                    int code = connection.getResponseCode();
                    if (code < 200 || code >= 300) {
                        throw new Exception("HTTP " + code);
                    }
                    String raw = readResponse(connection);
                    JSONObject object = new JSONObject(raw);
                    boolean hasPublished = object.optBoolean("has_published", false);
                    String versionTag = object.optString("version_tag", "");
                    boolean shouldShowUpdate = hasPublished
                            && !versionTag.isEmpty()
                            && !PublishedModelSync.isPublishedModelVersionInstalled(
                                    MainActivity.this,
                                    sp,
                                    username,
                                    versionTag
                            );
                    runOnUiThread(() -> {
                        availablePublishedVersionTag = shouldShowUpdate ? versionTag : "";
                        updateModelUpdateUi(shouldShowUpdate);
                    });
                } finally {
                    connection.disconnect();
                }
            } catch (Exception e) {
                Log.d(TAG, "Model update check skipped: " + e.getMessage());
            } finally {
                isCheckingModelUpdate = false;
                runOnUiThread(this::scheduleNextModelUpdateCheck);
            }
        }).start();
    }

    private void onModelUpdateClicked() {
        if (availablePublishedVersionTag.isEmpty()) {
            return;
        }
        String username = getLoggedInUsername();
        if (username.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, DownloadActivity.class);
        intent.putExtra(PublishedModelSync.EXTRA_DOWNLOAD_MODE, PublishedModelSync.DOWNLOAD_MODE_PUBLISHED_MODEL);
        intent.putExtra(PublishedModelSync.EXTRA_PUBLISHED_MODEL_USERNAME, username);
        intent.putExtra(PublishedModelSync.EXTRA_PUBLISHED_MODEL_VERSION_TAG, availablePublishedVersionTag);
        startActivity(intent);
    }

    private void updateModelUpdateUi(boolean hasUpdate) {
        if (btnModelUpdate == null || ivModelUpdateFlame == null) {
            return;
        }
        btnModelUpdate.setEnabled(true);
        if (hasUpdate) {
            btnModelUpdate.setPadding(dpToPx(12), dpToPx(10), dpToPx(12), dpToPx(10));
            btnModelUpdate.setText(R.string.model_update_available);
            btnModelUpdate.setContentDescription(getString(R.string.model_update_ready));
            ivModelUpdateFlame.setVisibility(View.VISIBLE);
        } else {
            btnModelUpdate.setPadding(0, 0, 0, 0);
            btnModelUpdate.setText("");
            btnModelUpdate.setContentDescription(getString(R.string.model_update_idle));
            ivModelUpdateFlame.setVisibility(View.GONE);
        }
        btnModelUpdate.requestLayout();
    }

    private void refreshModelSpinner(boolean preferPublishedModel) {
        ArrayList<File> tfliteFiles = getFilesWithExtension(sdcardDataFolder, ".tflite");
        ArrayAdapter<File> adapter = getFileArrayAdapter(tfliteFiles);
        spinnerTflite.setAdapter(adapter);
        File preferred = preferPublishedModel ? getPublishedModelFile() : selectedTfliteFile;
        selectedTfliteFile = ModelSelection.resolveSelectedModel(preferred, tfliteFiles);
        btnRecord.setEnabled(selectedTfliteFile != null);
        if (selectedTfliteFile != null) {
            int position = adapter.getPosition(selectedTfliteFile);
            spinnerTflite.setSelection(Math.max(position, 0), false);
            sp.edit().putString("modelName", selectedTfliteFile.getName()).apply();
            deinitModel();
            initModel();
        }
    }

    private File getPublishedModelFile() {
        return PublishedModelSync.getPublishedModelFile(this);
    }

    private String getLoggedInUsername() {
        if (sp == null) {
            return "";
        }
        String username = sp.getString(PREF_USERNAME, "");
        return username == null ? "" : username.trim();
    }

    private String readResponse(HttpURLConnection connection) throws Exception {
        InputStream stream = connection.getInputStream();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
