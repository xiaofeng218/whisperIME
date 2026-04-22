package com.whispertflite.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class EndpointConfigTest {
    @Test
    public void normalizeUrl_trimsAndRemovesTrailingSlash() {
        assertEquals("http://example.com:8000",
                EndpointConfig.normalizeUrl("  http://example.com:8000/  "));
    }

    @Test
    public void normalizeUrl_rejectsNonHttpSchemes() {
        assertEquals("", EndpointConfig.normalizeUrl("ftp://example.com"));
    }

    @Test
    public void normalizeUrl_rejectsProtocolOnly() {
        assertEquals("", EndpointConfig.normalizeUrl("https://"));
    }
}
