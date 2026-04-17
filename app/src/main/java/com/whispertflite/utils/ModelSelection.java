package com.whispertflite.utils;

import java.io.File;
import java.util.ArrayList;

public final class ModelSelection {
    private ModelSelection() {
    }

    public static File resolveSelectedModel(File preferredModel, ArrayList<File> availableModels) {
        if (preferredModel != null && availableModels.contains(preferredModel)) {
            return preferredModel;
        }
        if (availableModels.isEmpty()) {
            return null;
        }
        return availableModels.get(0);
    }
}
