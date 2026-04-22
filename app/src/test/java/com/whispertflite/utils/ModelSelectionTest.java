package com.whispertflite.utils;

import org.junit.Test;

import java.io.File;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ModelSelectionTest {
    @Test
    public void resolveSelectedModel_returnsPreferredWhenAvailable() {
        File preferred = new File("/tmp/whisper-small.tflite");
        ArrayList<File> available = new ArrayList<>();
        available.add(preferred);

        assertEquals(preferred, ModelSelection.resolveSelectedModel(preferred, available));
    }

    @Test
    public void resolveSelectedModel_fallsBackToFirstAvailableModel() {
        File preferred = new File("/tmp/missing.tflite");
        File fallback = new File("/tmp/whisper-small.tflite");
        ArrayList<File> available = new ArrayList<>();
        available.add(fallback);

        assertEquals(fallback, ModelSelection.resolveSelectedModel(preferred, available));
    }

    @Test
    public void resolveSelectedModel_returnsNullWhenNoModelsExist() {
        assertNull(ModelSelection.resolveSelectedModel(new File("/tmp/missing.tflite"), new ArrayList<>()));
    }
}
