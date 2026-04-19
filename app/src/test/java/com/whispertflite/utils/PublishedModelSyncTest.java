package com.whispertflite.utils;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PublishedModelSyncTest {

    @Test
    public void getModelScopeVersionInfoUrl_buildsExpectedUrl() {
        String url = PublishedModelSync.getModelScopeVersionInfoUrl("alice");

        assertEquals(
                "https://www.modelscope.cn/api/v1/models/hanxiaofeng218/CareSpeech-ASR/repo?Revision=master&FilePath=alice%2Fversion.json",
                url
        );
    }

    @Test
    public void extractVersionTag_returnsVersionTagFromValidJson() {
        String versionTag = PublishedModelSync.extractVersionTag("{\"version_tag\":\"1713520000\"}");

        assertEquals("1713520000", versionTag);
    }

    @Test
    public void extractVersionTag_returnsEmptyStringForInvalidPayload() {
        String versionTag = PublishedModelSync.extractVersionTag("not-json");

        assertEquals("", versionTag);
    }
}
