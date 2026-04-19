# ModelScope Update Metadata Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace the app's update-info source with a per-user `version.json` file in ModelScope while keeping published model downloads on ModelScope and preserving the existing UI flow.

**Architecture:** `PublishedModelSync` becomes the single place that knows how to build ModelScope URLs and parse the metadata payload. `MainActivity` stops calling the backend update API and instead downloads `version.json`, using its `version_tag` to decide whether to show the update badge. The download flow remains unchanged except that it still downloads the fixed `.tflite` path from ModelScope.

**Tech Stack:** Android Java, org.json, SharedPreferences, HttpURLConnection, JUnit 4

---

### Task 1: Add metadata helpers and tests

**Files:**
- Modify: `app/src/main/java/com/whispertflite/utils/PublishedModelSync.java`
- Create: `app/src/test/java/com/whispertflite/utils/PublishedModelSyncTest.java`

**Step 1: Write the failing tests**

- Add one test covering the generated `version.json` URL.
- Add one test covering version-tag parsing from a valid JSON payload.
- Add one test covering empty/invalid payload fallback.

**Step 2: Run the test command to verify failure**

Run: `./gradlew test --tests="com.whispertflite.utils.PublishedModelSyncTest"`

Expected: fail initially because the new helper methods do not exist yet. If the wrapper is unavailable, record that verification is blocked by missing build tooling.

**Step 3: Implement minimal helpers**

- Add a helper that builds the ModelScope `version.json` URL for a given username.
- Add a helper that extracts `version_tag` from raw JSON text.

**Step 4: Re-run the test command**

Run: `./gradlew test --tests="com.whispertflite.utils.PublishedModelSyncTest"`

Expected: pass, or record the same tooling limitation if build tooling is still unavailable.

### Task 2: Switch update checks to ModelScope metadata

**Files:**
- Modify: `app/src/main/java/com/whispertflite/MainActivity.java`

**Step 1: Replace the update-info URL**

- Stop calling `/api/latest_model_info`.
- Call the new `PublishedModelSync` helper for `<username>/version.json`.

**Step 2: Handle response semantics**

- 200: parse `version_tag`
- 404: treat as “no published version”
- Other non-2xx: log and hide the update badge

**Step 3: Preserve local comparison**

- Continue comparing remote `version_tag` against the locally stored version tag and local custom-model file presence.

**Step 4: Keep the rest of the flow unchanged**

- Do not change the click behavior, activity navigation, or download-page handoff.

### Task 3: Static verification and regression check

**Files:**
- Modify: none unless verification finds an issue

**Step 1: Confirm no remaining dependency on `latest_model_info`**

Run: `rg -n "latest_model_info" app/src/main/java/com/whispertflite`

Expected: no matches in the update-check path.

**Step 2: Confirm ModelScope URLs are centralized**

Run: `rg -n "version.json|whisper_model.tflite|modelscope.cn/api/v1/models" app/src/main/java/com/whispertflite`

Expected: URL building should live in `PublishedModelSync`.

**Step 3: Attempt available test/build verification**

Run:
- `./gradlew test --tests="com.whispertflite.utils.PublishedModelSyncTest"`
- `./gradlew assembleDebug`

Expected: pass if build tooling exists; otherwise explicitly report that Gradle wrapper or Gradle installation is missing.
