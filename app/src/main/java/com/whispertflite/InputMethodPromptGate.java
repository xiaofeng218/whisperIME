package com.whispertflite;

public final class InputMethodPromptGate {
    private static boolean hasPromptedThisProcess = false;

    private InputMethodPromptGate() {
    }

    public static synchronized boolean consumeShouldPrompt() {
        if (hasPromptedThisProcess) {
            return false;
        }
        hasPromptedThisProcess = true;
        return true;
    }

    static synchronized void resetForTests() {
        hasPromptedThisProcess = false;
    }
}
