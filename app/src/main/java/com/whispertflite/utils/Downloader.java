package com.whispertflite.utils;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import com.whispertflite.R;
import com.whispertflite.databinding.ActivityDownloadBinding;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


@SuppressWarnings("ResultOfMethodCallIgnored")
public class Downloader {
    public interface FileDownloadListener {
        void onProgress(long downloadedBytes, long totalBytes, int percent, boolean indeterminate);
        void onSuccess(File destination);
        void onError(String message);
    }

    static final String modelMultiLingualBaseOLD = "whisper-base.tflite"; //Todo Remove ...OLD... stuff later
    static final String modelMultiLingualBaseOLD2 = "whisper-base.EUROPEAN_UNION.tflite"; //Todo Remove ...OLD... stuff later
    static final String modelMultiLingualBase = "whisper-base.TOP_WORLD.tflite";
    static final String modelMultiLingualSmallOLD = "whisper-small.tflite";
    static final String modelMultiLingualSmall = "whisper-small.TOP_WORLD.tflite";
    static final String modelEnglishOnly = "whisper-tiny.en.tflite";
    static final String modelMultiLingualBaseURL = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-base.TOP_WORLD.tflite";
    static final String modelMultiLingualSmallURL = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-small.TOP_WORLD.tflite";
    static final String modelEnglishOnlyURL = "https://huggingface.co/DocWolle/whisper_tflite_models/resolve/main/whisper-tiny.en.tflite";
    
    // Custom Server Constants
    static final String modelCustomName = "whisper-small.tflite";
    static String modelCustomMD5 = "c3709c3bf90f76b3a7a2f6291234b490"; 
    
    static final String modelMultiLingualBaseOLDMD5 = "4b4fddfac6a24ffecc4972bc2137ba04";
    static final String modelMultiLingualBaseOLD2MD5 = "82adc0d42761f6d83fecd76d0325bcf5";
    static final String modelMultiLingualBaseMD5 = "9e43f385a916ac4b2e48760ce1fa70fc";
    static final String modelMultiLingualSmallOLDMD5 = "c4f948b3b42e7536bcedf78eec9481a6";
    static final String modelMultiLingualSmallMD5 = "d3badbb86c9bcc7312c19167acac7133";
    static final String modelEnglishOnlyMD5 ="2e745cdd5dfe2f868f47caa7a199f91a";
    static final long modelMultiLingualBaseSize = 107564368;
    static final long modelMultiLingualSmallSize = 307408944;
    static final long modelEnglishOnlySize = 41486616;
    static long downloadModelMultiLingualBaseSize = 0L;
    static long downloadModelMultiLingualSmallSize = 0L;
    static long downloadModelEnglishOnlySize = 0L;
    static long downloadModelCustomSize = 0L;
    static long expectedModelMultiLingualBaseSize = modelMultiLingualBaseSize;
    static long expectedModelMultiLingualSmallSize = modelMultiLingualSmallSize;
    static long expectedModelEnglishOnlySize = modelEnglishOnlySize;
    static long expectedModelCustomSize = 150 * 1024 * 1024;
    static boolean modelMultiLingualBaseFinished = false;
    static boolean modelEnglishOnlyFinished = false;
    static boolean modelMultiLingualSmallFinished = false;
    static boolean modelCustomFinished = false;

    private static final boolean DOWNLOAD_HF_MODELS = false; // Toggle this to enable/disable HF downloads

    public static boolean checkUpdate(final Activity activity) {
        if (DOWNLOAD_HF_MODELS) {
            File modelMultiLingualBaseFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBase);
            File modelMultiLingualSmallFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualSmall);
            if (!modelMultiLingualBaseFile.exists() || !modelMultiLingualSmallFile.exists()) {
                return true;   //update available
            }
        }
        File modelCustomFile = new File(activity.getExternalFilesDir(null) + "/" + modelCustomName);
        if (!modelCustomFile.exists()) {
            return true;
        }
        return false;  //no update
    }
    public static boolean checkModels(final Activity activity) {
        copyAssetsToSdcard(activity);
        File modelMultiLingualBaseFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBase);
        File modelMultiLingualBaseOLDFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBaseOLD);
        File modelMultiLingualBaseOLD2File = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBaseOLD2);
        File modelMultiLingualSmallOLDFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualSmallOLD);
        File modelMultiLingualSmallFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualSmall);
        File modelEnglishOnlyFile = new File(activity.getExternalFilesDir(null) + "/" + modelEnglishOnly);
        File modelCustomFile = new File(activity.getExternalFilesDir(null) + "/" + modelCustomName);

        String calcModelMultiLingualBaseMD5 = "";
        String calcModelMultiLingualBaseOLDMD5 = "";
        String calcModelMultiLingualBaseOLD2MD5 = "";
        String calcModelMultiLingualSmallMD5 = "";
        String calcModelMultiLingualSmallOLDMD5 = "";
        String calcModelEnglishOnlyMD5 = "";
        String calcModelCustomMD5 = "";

        if (modelMultiLingualBaseFile.exists()) {
            try {
                calcModelMultiLingualBaseMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualBaseFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        if (modelMultiLingualBaseOLDFile.exists()) {
            try {
                calcModelMultiLingualBaseOLDMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualBaseOLDFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        if (modelMultiLingualBaseOLD2File.exists()) {
            try {
                calcModelMultiLingualBaseOLD2MD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualBaseOLD2File.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        if (modelMultiLingualSmallFile.exists()) {
            try {
                calcModelMultiLingualSmallMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualSmallFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        boolean shouldTreatSmallOldAsLegacy = !modelMultiLingualSmallOLD.equals(modelCustomName);

        if (modelMultiLingualSmallOLDFile.exists() && shouldTreatSmallOldAsLegacy) {
            try {
                calcModelMultiLingualSmallOLDMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualSmallOLDFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        if (modelEnglishOnlyFile.exists()) {
            try {
                calcModelEnglishOnlyMD5 = calculateMD5(String.valueOf(Paths.get(modelEnglishOnlyFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }
        boolean shouldValidateCustomModelMd5 = DOWNLOAD_HF_MODELS;

        if (modelCustomFile.exists() && shouldValidateCustomModelMd5) {
            try {
                calcModelCustomMD5 = calculateMD5(String.valueOf(Paths.get(modelCustomFile.getPath())));
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            }
        }

        if (modelMultiLingualBaseOLDFile.exists() && !(calcModelMultiLingualBaseOLDMD5.equals(modelMultiLingualBaseOLDMD5))) { modelMultiLingualBaseOLDFile.delete();}
        if (modelMultiLingualBaseOLD2File.exists() && !(calcModelMultiLingualBaseOLD2MD5.equals(modelMultiLingualBaseOLD2MD5))) { modelMultiLingualBaseOLD2File.delete();}
        if (modelMultiLingualSmallOLDFile.exists() && shouldTreatSmallOldAsLegacy && !(calcModelMultiLingualSmallOLDMD5.equals(modelMultiLingualSmallOLDMD5))) { modelMultiLingualSmallOLDFile.delete();}
        if (modelMultiLingualBaseFile.exists() && !(calcModelMultiLingualBaseMD5.equals(modelMultiLingualBaseMD5))) { modelMultiLingualBaseFile.delete(); modelMultiLingualBaseFinished = false;}
        if (modelMultiLingualSmallFile.exists() && !(calcModelMultiLingualSmallMD5.equals(modelMultiLingualSmallMD5))) { modelMultiLingualSmallFile.delete(); modelMultiLingualSmallFinished = false;}
        if (modelEnglishOnlyFile.exists() && !calcModelEnglishOnlyMD5.equals(modelEnglishOnlyMD5)) { modelEnglishOnlyFile.delete(); modelEnglishOnlyFinished = false; }
        if (modelCustomFile.exists() && shouldValidateCustomModelMd5 && !calcModelCustomMD5.equals(modelCustomMD5)) { modelCustomFile.delete(); modelCustomFinished = false; }

        boolean hasValidSlowModel = calcModelMultiLingualSmallMD5.equals(modelMultiLingualSmallMD5)
                || (shouldTreatSmallOldAsLegacy && calcModelMultiLingualSmallOLDMD5.equals(modelMultiLingualSmallOLDMD5));
        boolean hfModelsOk = !DOWNLOAD_HF_MODELS || hasValidSlowModel
                && (calcModelMultiLingualBaseMD5.equals(modelMultiLingualBaseMD5) || calcModelMultiLingualBaseOLDMD5.equals(modelMultiLingualBaseOLDMD5) || calcModelMultiLingualBaseOLD2MD5.equals(modelMultiLingualBaseOLD2MD5))
                && calcModelEnglishOnlyMD5.equals(modelEnglishOnlyMD5);
        boolean customModelOk = modelCustomFile.exists() && (!shouldValidateCustomModelMd5 || calcModelCustomMD5.equals(modelCustomMD5));

        if (customModelOk) modelCustomFinished = true;
        if (!DOWNLOAD_HF_MODELS) {
            modelMultiLingualBaseFinished = true;
            modelMultiLingualSmallFinished = true;
            modelEnglishOnlyFinished = true;
        } else {
            if (calcModelMultiLingualBaseMD5.equals(modelMultiLingualBaseMD5)) modelMultiLingualBaseFinished = true;
            if (calcModelMultiLingualSmallMD5.equals(modelMultiLingualSmallMD5)) modelMultiLingualSmallFinished = true;
            if (calcModelEnglishOnlyMD5.equals(modelEnglishOnlyMD5)) modelEnglishOnlyFinished = true;
        }

        return hfModelsOk && customModelOk;
    }

    public static void deleteOldModels(final Activity activity){
        File modelMultiLingualBaseOLDFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBaseOLD);
        if (modelMultiLingualBaseOLDFile.exists()) modelMultiLingualBaseOLDFile.delete();
        File modelMultiLingualBaseOLD2File = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBaseOLD2);
        if (modelMultiLingualBaseOLD2File.exists()) modelMultiLingualBaseOLD2File.delete();
        File modelMultiLingualSmallOLDFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualSmallOLD);
        if (modelMultiLingualSmallOLDFile.exists() && !modelMultiLingualSmallOLD.equals(modelCustomName)) {
            modelMultiLingualSmallOLDFile.delete();
        }
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(activity);
        sp.edit().remove("modelName").apply();
        sp.edit().remove("recognitionServiceModelName").apply();
    }

    public static void downloadModels(final Activity activity, ActivityDownloadBinding binding) {
        checkModels(activity);

        binding.downloadProgress.setProgress(0);
        binding.downloadButton.setEnabled(false);
        Thread setupThread = new Thread(() -> {
            String customModelUrl = EndpointConfig.getCustomModelUrl(activity);
            initializeExpectedSizes(activity);
            initializeDownloadedSizes(activity);
            updateDownloadProgress(activity, binding);

            if (DOWNLOAD_HF_MODELS) {
            File modelMultiLingualBaseFile = new File(activity.getExternalFilesDir(null)+ "/" + modelMultiLingualBase);
            if (!modelMultiLingualBaseFile.exists()) {
                modelMultiLingualBaseFinished = false;
                Log.d("WhisperASR", "multi-lingual base model file does not exist");
                Thread thread = new Thread(() -> {
                    try {
                        URL url = new URL(modelMultiLingualBaseURL);
                        Log.d("WhisperASR", "Download model");

                        URLConnection ucon = url.openConnection();
                        ucon.setReadTimeout(5000);
                        ucon.setConnectTimeout(10000);

                        InputStream is = ucon.getInputStream();
                        BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                        modelMultiLingualBaseFile.createNewFile();

                        FileOutputStream outStream = new FileOutputStream(modelMultiLingualBaseFile);
                        byte[] buff = new byte[5 * 1024];

                        int len;
                        while ((len = inStream.read(buff)) != -1) {
                            outStream.write(buff, 0, len);
                            if (modelMultiLingualBaseFile.exists()) downloadModelMultiLingualBaseSize = modelMultiLingualBaseFile.length();
                            updateDownloadProgress(activity, binding);
                        }
                        outStream.flush();
                        outStream.close();
                        inStream.close();
                        String calcModelMultiLingualBaseMD5="";
                        if (modelMultiLingualBaseFile.exists()) {
                            calcModelMultiLingualBaseMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualBaseFile.getPath())));
                        } else {
                            throw new IOException();
                        }

                        if (!(calcModelMultiLingualBaseMD5.equals(modelMultiLingualBaseMD5))){
                            modelMultiLingualBaseFile.delete();
                            modelMultiLingualBaseFinished = false;
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                                binding.downloadButton.setEnabled(true);
                            });
                        } else {
                            modelMultiLingualBaseFinished = true;
                            activity.runOnUiThread(() -> {
                                if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) notifyDownloadFinished(activity, binding);
                            });
                        }
                    } catch (NoSuchAlgorithmException | IOException i) {
                        modelMultiLingualBaseFile.delete();
                        modelMultiLingualBaseFinished = false;
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                            binding.downloadButton.setEnabled(true);
                        });                        Log.w("WhisperASR", activity.getResources().getString(R.string.error_download), i);
                    }
                });
                thread.start();
            } else {
                downloadModelMultiLingualBaseSize = modelMultiLingualBaseSize;
                modelMultiLingualBaseFinished = true;
                updateDownloadProgress(activity, binding);
                activity.runOnUiThread(() -> {
                    if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                });
            }

            File modelMultiLingualSmallFile = new File(activity.getExternalFilesDir(null)+ "/" + modelMultiLingualSmall);
            if (!modelMultiLingualSmallFile.exists()) {
                modelMultiLingualSmallFinished = false;
                Log.d("WhisperASR", "multi-lingual small model file does not exist");
                Thread thread = new Thread(() -> {
                    try {
                        URL url = new URL(modelMultiLingualSmallURL);
                        Log.d("WhisperASR", "Download model");

                        URLConnection ucon = url.openConnection();
                        ucon.setReadTimeout(5000);
                        ucon.setConnectTimeout(10000);

                        InputStream is = ucon.getInputStream();
                        BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                        modelMultiLingualSmallFile.createNewFile();

                        FileOutputStream outStream = new FileOutputStream(modelMultiLingualSmallFile);
                        byte[] buff = new byte[5 * 1024];

                        int len;
                        while ((len = inStream.read(buff)) != -1) {
                            outStream.write(buff, 0, len);
                            if (modelMultiLingualSmallFile.exists()) downloadModelMultiLingualSmallSize = modelMultiLingualSmallFile.length();
                            updateDownloadProgress(activity, binding);
                        }
                        outStream.flush();
                        outStream.close();
                        inStream.close();
                        String calcModelMultiLingualSmallMD5="";
                        if (modelMultiLingualSmallFile.exists()) {
                            calcModelMultiLingualSmallMD5 = calculateMD5(String.valueOf(Paths.get(modelMultiLingualSmallFile.getPath())));
                        } else {
                            throw new IOException();
                        }

                        if (!(calcModelMultiLingualSmallMD5.equals(modelMultiLingualSmallMD5))){
                            modelMultiLingualSmallFile.delete();
                            modelMultiLingualSmallFinished = false;
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                                binding.downloadButton.setEnabled(true);
                            });
                        } else {
                            modelMultiLingualSmallFinished = true;
                            activity.runOnUiThread(() -> {
                                if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) notifyDownloadFinished(activity, binding);
                            });
                        }
                    } catch (NoSuchAlgorithmException | IOException i) {
                        modelMultiLingualSmallFile.delete();
                        modelMultiLingualSmallFinished = false;
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                            binding.downloadButton.setEnabled(true);
                        });                        Log.w("WhisperASR", activity.getResources().getString(R.string.error_download), i);
                    }
                });
                thread.start();
            } else {
                downloadModelMultiLingualSmallSize = modelMultiLingualSmallSize;
                modelMultiLingualSmallFinished = true;
                updateDownloadProgress(activity, binding);
                activity.runOnUiThread(() -> {
                    if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                });
            }

            File modelEnglishOnlyFile = new File(activity.getExternalFilesDir(null) + "/" + modelEnglishOnly);
            if (!modelEnglishOnlyFile.exists()) {
                modelEnglishOnlyFinished = false;
                Log.d("WhisperASR", "English only model file does not exist");
                Thread thread = new Thread(() -> {
                    try {
                        URL url = new URL(modelEnglishOnlyURL);
                        Log.d("WhisperASR", "Download English only model");

                        URLConnection ucon = url.openConnection();
                        ucon.setReadTimeout(5000);
                        ucon.setConnectTimeout(10000);

                        InputStream is = ucon.getInputStream();
                        BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                        modelEnglishOnlyFile.createNewFile();

                        FileOutputStream outStream = new FileOutputStream(modelEnglishOnlyFile);
                        byte[] buff = new byte[5 * 1024];

                        int len;
                        while ((len = inStream.read(buff)) != -1) {
                            outStream.write(buff, 0, len);
                            if (modelEnglishOnlyFile.exists()) downloadModelEnglishOnlySize = modelEnglishOnlyFile.length();
                            updateDownloadProgress(activity, binding);
                        }
                        outStream.flush();
                        outStream.close();
                        inStream.close();

                        String calcEnglishOnlyModelMD5="";
                        if (modelEnglishOnlyFile.exists()) {
                            calcEnglishOnlyModelMD5 = calculateMD5(String.valueOf(Paths.get(modelEnglishOnlyFile.getPath())));
                        } else {
                            throw new IOException();
                        }

                        if (!calcEnglishOnlyModelMD5.equals(modelEnglishOnlyMD5)){
                            modelEnglishOnlyFile.delete();
                            modelEnglishOnlyFinished = false;
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                                binding.downloadButton.setEnabled(true);
                            });
                        } else {
                            modelEnglishOnlyFinished = true;
                            activity.runOnUiThread(() -> {
                                if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) notifyDownloadFinished(activity, binding);
                            });
                        }
                    } catch (NoSuchAlgorithmException | IOException i) {
                        modelEnglishOnlyFile.delete();
                        modelEnglishOnlyFinished = false;
                        activity.runOnUiThread(() -> {
                            Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                            binding.downloadButton.setEnabled(true);
                        });                        Log.w("WhisperASR", activity.getResources().getString(R.string.error_download), i);
                    }
                });
                thread.start();
            } else {
                downloadModelEnglishOnlySize = modelEnglishOnlySize;
                modelEnglishOnlyFinished = true;
                updateDownloadProgress(activity, binding);
                activity.runOnUiThread(() -> {
                    if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) binding.buttonStart.setVisibility(View.VISIBLE);
                });
            }
        } else {
            // If HF downloads are disabled, mark them as finished
            modelMultiLingualBaseFinished = true;
            modelMultiLingualSmallFinished = true;
            modelEnglishOnlyFinished = true;
        }

        File modelCustomFile = new File(activity.getExternalFilesDir(null) + "/" + modelCustomName);
        if (!modelCustomFile.exists()) {
            modelCustomFinished = false;
            Log.d("WhisperASR", "Custom model file does not exist");
                Thread thread = new Thread(() -> {
                    try {
                    URL url = new URL(customModelUrl);
                    Log.d("WhisperASR", "Download Custom model: " + customModelUrl);

                    URLConnection ucon = url.openConnection();
                    ucon.setReadTimeout(5000);
                    ucon.setConnectTimeout(10000);

                    InputStream is = ucon.getInputStream();
                    BufferedInputStream inStream = new BufferedInputStream(is, 1024 * 5);

                    modelCustomFile.createNewFile();

                    FileOutputStream outStream = new FileOutputStream(modelCustomFile);
                    byte[] buff = new byte[5 * 1024];

                    int len;
                    while ((len = inStream.read(buff)) != -1) {
                        outStream.write(buff, 0, len);
                        if (modelCustomFile.exists()) downloadModelCustomSize = modelCustomFile.length();
                        updateDownloadProgress(activity, binding);
                    }
                    outStream.flush();
                    outStream.close();
                    inStream.close();

                    if (!modelCustomMD5.isEmpty()) {
                        String calcMD5 = calculateMD5(String.valueOf(Paths.get(modelCustomFile.getPath())));
                        if (!calcMD5.equals(modelCustomMD5)) {
                            modelCustomFile.delete();
                            modelCustomFinished = false;
                            activity.runOnUiThread(() -> {
                                Toast.makeText(activity, "Custom model MD5 mismatch", Toast.LENGTH_SHORT).show();
                                binding.downloadButton.setEnabled(true);
                            });
                            return;
                        }
                    }

                    modelCustomFinished = true;
                    activity.runOnUiThread(() -> {
                        if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) notifyDownloadFinished(activity, binding);
                    });
                } catch (NoSuchAlgorithmException | IOException i) {
                    modelCustomFile.delete();
                    modelCustomFinished = false;
                    activity.runOnUiThread(() -> {
                        Toast.makeText(activity, activity.getResources().getString(R.string.error_download), Toast.LENGTH_SHORT).show();
                        binding.downloadButton.setEnabled(true);
                        binding.downloadButton.setClickable(true);
                    });                    Log.w("WhisperASR", "Error downloading custom model", i);
                }
            });
            thread.start();
        } else {
            modelCustomFinished = true;
            updateDownloadProgress(activity, binding);
            activity.runOnUiThread(() -> {
                if (modelEnglishOnlyFinished && modelMultiLingualSmallFinished && modelMultiLingualBaseFinished && modelCustomFinished) notifyDownloadFinished(activity, binding);
            });
        }
        });
        setupThread.start();
    }

    public static void downloadFileAsync(Context context, String fileUrl, File destination, FileDownloadListener listener) {
        Thread downloadThread = new Thread(() -> {
            HttpURLConnection connection = null;
            File tempFile = new File(destination.getAbsolutePath() + ".download");
            try {
                URL url = new URL(fileUrl);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(15000);
                connection.connect();

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    throw new IOException("HTTP " + responseCode);
                }
                long totalBytes = connection.getContentLengthLong();
                boolean indeterminate = totalBytes <= 0;
                listener.onProgress(0L, totalBytes, 0, indeterminate);

                File parent = destination.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                if (tempFile.exists()) {
                    tempFile.delete();
                }

                try (InputStream inputStream = new BufferedInputStream(connection.getInputStream(), 1024 * 5);
                     OutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[8 * 1024];
                    int len;
                    long downloadedBytes = 0L;
                    while ((len = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, len);
                        downloadedBytes += len;
                        if (!indeterminate && totalBytes > 0) {
                            int percent = (int) Math.min(100L, (downloadedBytes * 100L) / totalBytes);
                            listener.onProgress(downloadedBytes, totalBytes, percent, false);
                        } else {
                            listener.onProgress(downloadedBytes, totalBytes, 0, true);
                        }
                    }
                    outputStream.flush();
                }

                if (destination.exists() && !destination.delete()) {
                    throw new IOException("无法替换旧模型文件");
                }
                if (!tempFile.renameTo(destination)) {
                    throw new IOException("无法写入模型文件");
                }
                listener.onProgress(totalBytes > 0 ? totalBytes : destination.length(), totalBytes, 100, false);
                listener.onSuccess(destination);
            } catch (Exception e) {
                Log.w("WhisperASR", "Error downloading file: " + fileUrl, e);
                if (tempFile.exists()) {
                    tempFile.delete();
                }
                String message = e.getMessage() == null ? context.getString(R.string.error_download) : e.getMessage();
                listener.onError(message);
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
        downloadThread.start();
    }

    public static void downloadPublishedModel(
            @NonNull Activity activity,
            ActivityDownloadBinding binding,
            @NonNull String username,
            @NonNull String versionTag
    ) {
        binding.downloadProgress.setProgress(0);
        binding.downloadProgress.setIndeterminate(true);
        binding.downloadButton.setEnabled(false);
        binding.downloadButton.setClickable(false);
        binding.downloadSize.setText(activity.getString(R.string.please_wait));

        String fileUrl = PublishedModelSync.getModelScopePublishedModelUrl(username);
        File destination = PublishedModelSync.getPublishedModelFile(activity);
        SharedPreferences preferences = PublishedModelSync.getPreferences(activity);

        downloadFileAsync(activity, fileUrl, destination, new FileDownloadListener() {
            @Override
            public void onProgress(long downloadedBytes, long totalBytes, int percent, boolean indeterminate) {
                activity.runOnUiThread(() -> {
                    binding.downloadProgress.setVisibility(View.VISIBLE);
                    binding.downloadSize.setVisibility(View.VISIBLE);
                    binding.downloadProgress.setIndeterminate(indeterminate);
                    if (!indeterminate) {
                        binding.downloadProgress.setProgress(percent);
                        binding.downloadSize.setText(activity.getString(
                                R.string.download_progress_bytes,
                                downloadedBytes / 1024 / 1024,
                                totalBytes / 1024 / 1024
                        ));
                    } else {
                        binding.downloadSize.setText(activity.getString(
                                R.string.download_progress_downloaded_only,
                                downloadedBytes / 1024 / 1024
                        ));
                    }
                });
            }

            @Override
            public void onSuccess(File file) {
                if (!versionTag.trim().isEmpty()) {
                    PublishedModelSync.setDownloadedPublishedVersionTag(preferences, username, versionTag);
                }
                activity.runOnUiThread(() -> {
                    binding.circularLoading.setVisibility(View.GONE);
                    binding.downloadProgress.setIndeterminate(false);
                    binding.downloadProgress.setProgress(100);
                    notifyDownloadFinished(activity, binding);
                });
            }

            @Override
            public void onError(String message) {
                activity.runOnUiThread(() -> {
                    binding.circularLoading.setVisibility(View.GONE);
                    binding.downloadButton.setVisibility(View.VISIBLE);
                    binding.downloadButton.setEnabled(true);
                    binding.downloadButton.setClickable(true);
                    binding.downloadProgress.setIndeterminate(false);
                    binding.downloadProgress.setProgress(0);
                    binding.downloadProgress.setVisibility(View.GONE);
                    binding.downloadSize.setVisibility(View.GONE);
                    Toast.makeText(
                            activity,
                            activity.getString(R.string.model_update_download_failed, message),
                            Toast.LENGTH_SHORT
                    ).show();
                });
            }
        });
    }

    private static void initializeExpectedSizes(Activity activity) {
        if (DOWNLOAD_HF_MODELS) {
            File modelMultiLingualBaseFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBase);
            File modelMultiLingualSmallFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualSmall);
            File modelEnglishOnlyFile = new File(activity.getExternalFilesDir(null) + "/" + modelEnglishOnly);

            expectedModelMultiLingualBaseSize = resolveExpectedSize(
                    modelMultiLingualBaseFile,
                    probeRemoteFileSize(modelMultiLingualBaseURL),
                    modelMultiLingualBaseSize
            );
            expectedModelMultiLingualSmallSize = resolveExpectedSize(
                    modelMultiLingualSmallFile,
                    probeRemoteFileSize(modelMultiLingualSmallURL),
                    modelMultiLingualSmallSize
            );
            expectedModelEnglishOnlySize = resolveExpectedSize(
                    modelEnglishOnlyFile,
                    probeRemoteFileSize(modelEnglishOnlyURL),
                    modelEnglishOnlySize
            );
        } else {
            expectedModelMultiLingualBaseSize = 0L;
            expectedModelMultiLingualSmallSize = 0L;
            expectedModelEnglishOnlySize = 0L;
        }

        File modelCustomFile = new File(activity.getExternalFilesDir(null) + "/" + modelCustomName);
        expectedModelCustomSize = resolveExpectedSize(
                modelCustomFile,
                probeRemoteFileSize(EndpointConfig.getCustomModelUrl(activity)),
                150 * 1024 * 1024
        );
    }

    private static void initializeDownloadedSizes(Activity activity) {
        if (DOWNLOAD_HF_MODELS) {
            File modelMultiLingualBaseFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualBase);
            File modelMultiLingualSmallFile = new File(activity.getExternalFilesDir(null) + "/" + modelMultiLingualSmall);
            File modelEnglishOnlyFile = new File(activity.getExternalFilesDir(null) + "/" + modelEnglishOnly);
            downloadModelMultiLingualBaseSize = modelMultiLingualBaseFile.exists() ? modelMultiLingualBaseFile.length() : 0L;
            downloadModelMultiLingualSmallSize = modelMultiLingualSmallFile.exists() ? modelMultiLingualSmallFile.length() : 0L;
            downloadModelEnglishOnlySize = modelEnglishOnlyFile.exists() ? modelEnglishOnlyFile.length() : 0L;
        } else {
            downloadModelMultiLingualBaseSize = 0L;
            downloadModelMultiLingualSmallSize = 0L;
            downloadModelEnglishOnlySize = 0L;
        }

        File modelCustomFile = new File(activity.getExternalFilesDir(null) + "/" + modelCustomName);
        downloadModelCustomSize = modelCustomFile.exists() ? modelCustomFile.length() : 0L;
    }

    private static void updateDownloadProgress(Activity activity, ActivityDownloadBinding binding) {
        activity.runOnUiThread(() -> {
            long totalDownloaded = getTotalDownloadedBytes();
            long totalExpected = getTotalExpectedBytes();
            binding.downloadSize.setText(totalDownloaded / 1024 / 1024 + " MB");
            binding.downloadProgress.setProgress(calculateProgressPercent(totalDownloaded, totalExpected));
        });
    }

    private static long getTotalDownloadedBytes() {
        return downloadModelEnglishOnlySize
                + downloadModelMultiLingualSmallSize
                + downloadModelMultiLingualBaseSize
                + downloadModelCustomSize;
    }

    private static long getTotalExpectedBytes() {
        return expectedModelEnglishOnlySize
                + expectedModelMultiLingualSmallSize
                + expectedModelMultiLingualBaseSize
                + expectedModelCustomSize;
    }

    static long resolveExpectedSize(File localFile, long remoteSize, long fallbackSize) {
        if (localFile.exists() && localFile.length() > 0) {
            return localFile.length();
        }
        if (remoteSize > 0) {
            return remoteSize;
        }
        return fallbackSize;
    }

    static int calculateProgressPercent(long downloadedBytes, long expectedBytes) {
        if (expectedBytes <= 0) {
            return 0;
        }
        int progress = (int) ((downloadedBytes * 100) / expectedBytes);
        return Math.min(progress, 100);
    }

    private static long probeRemoteFileSize(String fileUrl) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(5000);
            connection.connect();
            long size = connection.getContentLengthLong();
            if (size > 0) {
                return size;
            }
        } catch (IOException e) {
            Log.w("WhisperASR", "Unable to probe file size via HEAD: " + fileUrl, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }

        connection = null;
        try {
            connection = (HttpURLConnection) new URL(fileUrl).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(5000);
            connection.connect();
            long size = parseContentRangeTotalSize(connection.getHeaderField("Content-Range"));
            if (size > 0) {
                return size;
            }
        } catch (IOException e) {
            Log.w("WhisperASR", "Unable to probe file size via GET: " + fileUrl, e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
        return -1L;
    }

    private static long parseContentRangeTotalSize(String contentRange) {
        if (contentRange == null) {
            return -1L;
        }
        int slashIndex = contentRange.lastIndexOf('/');
        if (slashIndex < 0 || slashIndex >= contentRange.length() - 1) {
            return -1L;
        }
        try {
            return Long.parseLong(contentRange.substring(slashIndex + 1));
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    public static String calculateMD5(String filePath) throws IOException, NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("MD5");
        try (InputStream is = new BufferedInputStream(new FileInputStream(filePath))) {
            byte[] buffer = new byte[8192]; // 8KB buffer
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
        }
        byte[] hash = md.digest();
        return new BigInteger(1, hash).toString(16);
    }

    // Copy assets to destination folder
    public static void copyAssetsToSdcard(Context context) {
        String[] extensions = {"bin"};
        File sdcardDataFolder = context.getExternalFilesDir(null);
        AssetManager assetManager = context.getAssets();

        try {
            // List all files in the assets folder once
            String[] assetFiles = assetManager.list("");
            if (assetFiles == null) return;

            for (String assetFileName : assetFiles) {
                // Check if file matches any of the provided extensions
                for (String extension : extensions) {
                    if (assetFileName.endsWith("." + extension)) {
                        File outFile = new File(sdcardDataFolder, assetFileName);

                        // Skip if file already exists
                        if (outFile.exists()) break;

                        // Copy the file from assets to the destination folder
                        try (InputStream inputStream = assetManager.open(assetFileName);
                             OutputStream outputStream = new FileOutputStream(outFile)) {

                            byte[] buffer = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                outputStream.write(buffer, 0, bytesRead);
                            }
                        }
                        break; // No need to check further extensions
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void notifyDownloadFinished(Activity activity, ActivityDownloadBinding binding) {
        try {
            // Use reflection to call showSuccessState if it exists (for DownloadActivity)
            activity.getClass().getMethod("showSuccessState").invoke(activity);
        } catch (Exception e) {
            // Fallback for other activities
            binding.buttonStart.setVisibility(View.VISIBLE);
        }
    }
}
