# Published Model Download Flow Design

## Goal

将主页面右下角的模型更新按钮改成“更新入口”，点击后跳转到下载页，由下载页统一承接发布模型的下载进度、完成态和返回主页面流程。

## Current Context

- `MainActivity` 已经具备发布模型轮询能力，并在检测到新版本时显示“更新 + 火苗”。
- 当前点击更新按钮会直接在 `MainActivity` 中调用 `Downloader.downloadFileAsync(...)` 下载 `whisper-custom.tflite`，按钮自身承担下载态和小型进度环。
- `DownloadActivity` 已有一套成熟的下载 UI，包括下载按钮、线性进度条、完成态和“开始/返回”按钮，但目前只面向首装模型下载。

## Options Considered

### 方案 1：保留主页面直接下载

- 优点：改动小。
- 缺点：主页面要同时负责检测、下载、进度、失败提示，按钮上的小进度信息也不够明显。

### 方案 2：主页面只跳转，下载页增加“发布模型更新模式”

- 优点：下载体验统一，复用现有下载页 UI 和完成态，主页面职责更清晰。
- 缺点：需要给 `DownloadActivity` 增加模式分支，并让 `Downloader` 支持新的入口。

### 方案 3：新建一个专用更新页面

- 优点：首装和更新流程完全隔离。
- 缺点：重复建设现有下载 UI，不符合当前仓库已有能力。

## Recommendation

选择方案 2。

`MainActivity` 继续轮询服务端 `/api/latest_model_info`，只负责判断“是否有更新”以及显示更新入口。点击更新按钮后，跳转到 `DownloadActivity`，并通过 `Intent` extra 显式进入“发布模型更新模式”。

## Design

### 1. MainActivity 职责收敛

- 保留发布模型轮询逻辑和火苗提示。
- 去掉按钮内的环形下载进度和直接下载逻辑。
- 按钮点击时，带上当前登录用户名和可用 `version_tag` 跳转到 `DownloadActivity`。
- 返回主页面后，依然通过既有轮询逻辑重新判断是否还有新版本。

### 2. DownloadActivity 增加双模式

- 默认模式：保持首装/普通模型下载逻辑不变。
- 发布模型更新模式：
  - 页面文案改成“下载定制模型更新”。
  - 使用已有下载按钮、线性进度条和成功态。
  - 点击下载后，覆盖本地固定文件 `whisper-custom.tflite`。
  - 下载成功后复用现有 `buttonStart` 返回链路，跳回主页面。

### 3. Downloader 复用下载能力

- 保留原 `downloadModels(...)` 不动，避免影响首装。
- 新增一个专用入口用于下载发布模型，内部复用现有 `downloadFileAsync(...)`。
- 这个入口负责：
  - 请求 `/api/download_published_model?username=...`
  - 将文件下载到 `whisper-custom.tflite`
  - 更新下载页线性进度条和大小文本
  - 成功后记录当前用户已下载的 `version_tag`

### 4. 本地模型策略

- 服务器发布模型在客户端始终统一成一个本地文件：`whisper-custom.tflite`
- `MainActivity` 中这个文件继续显示为“定制”
- 新版本下载时直接覆盖旧版定制模型，不新增历史版本项

### 5. 鲁棒性

- 主页面轮询失败：静默记录日志，不打断识别流程。
- 下载页下载失败：显示现有下载错误提示，并保留旧定制模型。
- 下载过程使用临时文件替换，确保中途失败不会损坏可用模型。

### 6. Return Flow

- `DownloadActivity.startMain(...)` 在普通首装模式下仍然跳到 `AuthActivity`
- 在发布模型更新模式下，改为跳回 `MainActivity`
- 可以使用 `Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP`，尽量复用已有主页面实例
