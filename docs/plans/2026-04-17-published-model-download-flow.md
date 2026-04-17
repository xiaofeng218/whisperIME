# Published Model Download Flow Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Move published-model downloads out of `MainActivity` and into `DownloadActivity` while preserving update detection on the main screen.

**Architecture:** `MainActivity` remains responsible for polling `/api/latest_model_info` and exposing a visual update affordance. `DownloadActivity` gains a second mode for published-model updates and reuses the existing download page UI to download and replace the single local custom model file.

**Tech Stack:** Android Java, Android Kotlin, ViewBinding, SharedPreferences, HttpURLConnection, Material Components

---

### Task 1: Add published-model download mode plumbing

**Files:**
- Modify: `app/src/main/java/com/whispertflite/DownloadActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`

**Step 1: Add constants and state for the new mode**

- Add intent extra keys for download mode, username, and version tag.
- Add a boolean or enum-like flag to determine whether the page is running in normal install mode or published-model update mode.

**Step 2: Parse the incoming intent in `onCreate`**

- Read the extras once and store them in fields.
- Keep the default path aligned with the current launcher behavior.

**Step 3: Update page text for published-model mode**

- Change title/subtitle/button labels when handling a published-model update.
- Keep the existing strings and layout for the first-run path.

**Step 4: Adjust resume logic**

- In normal mode, keep today’s `checkModels()` / `checkUpdate()` behavior.
- In published-model mode, skip the auto-advance-to-auth branch and instead show either a ready-to-download state or a success state if the requested version is already installed.

### Task 2: Reuse download page UI for published-model downloads

**Files:**
- Modify: `app/src/main/java/com/whispertflite/DownloadActivity.kt`
- Modify: `app/src/main/java/com/whispertflite/utils/Downloader.java`

**Step 1: Add a dedicated published-model download helper to `Downloader`**

- Reuse `downloadFileAsync(...)`.
- Accept the target username, destination file, current version tag, and `ActivityDownloadBinding`.
- Update `download_size`, `download_progress`, loading spinner, and completion state from this shared helper.

**Step 2: Persist the downloaded version tag**

- Store the downloaded `version_tag` per username in shared preferences using the same prefix already used by `MainActivity`.
- Only mark the version as installed after a successful file replacement.

**Step 3: Hook `DownloadActivity.download(...)` into mode-specific behavior**

- Normal mode: keep calling `Downloader.downloadModels(...)`.
- Published-model mode: call the new dedicated helper.

**Step 4: Keep failure handling robust**

- Surface a user-friendly download failure message on the download page.
- Re-enable the download button on failure.
- Do not overwrite the previous custom model if the new download fails.

### Task 3: Make MainActivity a detector and navigator only

**Files:**
- Modify: `app/src/main/java/com/whispertflite/MainActivity.java`
- Modify: `app/src/main/res/layout/activity_main.xml`

**Step 1: Remove the in-button download flow**

- Delete the direct call to `Downloader.downloadFileAsync(...)`.
- Remove the small progress-ring usage and any download-only UI state that is no longer needed.

**Step 2: Keep polling and update affordance**

- Preserve `checkForPublishedModelUpdate()`.
- Continue showing the fire badge and update CTA when a newer `version_tag` exists than the locally stored version.

**Step 3: Replace click behavior with navigation**

- Build an intent for `DownloadActivity`.
- Pass mode=`published_model`, username, and latest available `version_tag`.
- Start the download page instead of downloading from the main screen.

**Step 4: Keep model list behavior unchanged**

- Preserve the single `定制` slot backed by `whisper-custom.tflite`.
- Keep spinner refresh logic so the downloaded custom model continues to appear correctly after returning from the download page.

### Task 4: Return flow and verification

**Files:**
- Modify: `app/src/main/java/com/whispertflite/DownloadActivity.kt`
- Test: manual verification only

**Step 1: Update `startMain(...)`**

- Normal mode: keep navigating to `AuthActivity`.
- Published-model mode: navigate back to `MainActivity` using flags that prefer an existing instance.

**Step 2: Verify no regression in launcher flow**

- Confirm the default launcher path remains `DownloadActivity -> AuthActivity -> MainActivity`.
- Confirm the update-entry path becomes `MainActivity -> DownloadActivity(published mode) -> MainActivity`.

**Step 3: Verify no build command is available if wrapper is missing**

- Attempt `./gradlew assembleDebug`.
- If the wrapper is unavailable, record that verification was limited to static inspection.
