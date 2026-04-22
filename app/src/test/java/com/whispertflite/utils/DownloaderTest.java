package com.whispertflite.utils;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertEquals;

public class DownloaderTest {
    @Test
    public void resolveExpectedSize_prefersLocalFileLengthWhenAvailable() throws Exception {
        File tempFile = File.createTempFile("downloader", ".bin");
        tempFile.deleteOnExit();
        try (FileOutputStream out = new FileOutputStream(tempFile)) {
            out.write(new byte[] {1, 2, 3});
        }
        assertEquals(tempFile.length(), Downloader.resolveExpectedSize(tempFile, 123L, 456L));
    }

    @Test
    public void resolveExpectedSize_fallsBackToRemoteSizeWhenLocalFileMissing() {
        File missingFile = new File(System.getProperty("java.io.tmpdir"), "missing-model-" + System.nanoTime() + ".tflite");
        assertEquals(123L, Downloader.resolveExpectedSize(missingFile, 123L, 456L));
    }

    @Test
    public void calculateProgressPercent_capsAtHundred() {
        assertEquals(100, Downloader.calculateProgressPercent(200L, 100L));
    }
}
