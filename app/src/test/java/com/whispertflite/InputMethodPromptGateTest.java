package com.whispertflite;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class InputMethodPromptGateTest {
    @After
    public void tearDown() {
        InputMethodPromptGate.resetForTests();
    }

    @Test
    public void consumeShouldPrompt_onlyReturnsTrueOncePerProcess() {
        assertTrue(InputMethodPromptGate.consumeShouldPrompt());
        assertFalse(InputMethodPromptGate.consumeShouldPrompt());
    }
}
