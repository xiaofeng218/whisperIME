# ModelScope Update Metadata Design

## Goal

让 Android 客户端直接从 ModelScope 仓库读取“是否有新版本”的元数据，不再依赖服务端 `latest_model_info` 接口；模型文件下载仍然继续来自 ModelScope。

## Decision

采用每个用户目录下固定放置一个 `version.json` 的方案：

- 仓库：`hanxiaofeng218/CareSpeech-ASR`
- 版本元数据路径：`<username>/version.json`
- 模型文件路径：`<username>/whisper_model.tflite`

客户端启动后先读取 `version.json`，比较其中的 `version_tag` 和本地已安装版本。只有发现新版时，右下角才显示更新火花。用户点击后，再从固定的 `whisper_model.tflite` 路径下载模型。

## Why This Design

- 不需要把版本号编码进文件名
- 不依赖 ModelScope 的 `ETag` 或 `Last-Modified` 等不稳定 HTTP 头
- 不需要继续依赖服务端更新接口
- 发布流程清晰：更新模型时，同时覆盖 `whisper_model.tflite` 和 `version.json`

## Metadata Format

最小字段集：

```json
{
  "version_tag": "1713520000"
}
```

可扩展字段：

```json
{
  "version_tag": "1713520000",
  "file_path": "alice/whisper_model.tflite"
}
```

当前客户端只依赖 `version_tag`。如果 `file_path` 缺失，仍然默认下载 `<username>/whisper_model.tflite`。

## Client Behavior

### Update Check

- 已登录时才检查
- 请求 `https://www.modelscope.cn/api/v1/models/hanxiaofeng218/CareSpeech-ASR/repo?Revision=master&FilePath=<username>/version.json`
- 404 视为“该用户尚未发布版本”，不报错、不显示火花
- 非 2xx 其他错误视为检查失败，记日志并静默隐藏火花

### Update Decision

- 读取 `version.json` 中的 `version_tag`
- 如果为空，不显示火花
- 如果与本地记录相同且本地 `whisper-custom.tflite` 存在，不显示火花
- 否则显示火花

### Download

- 下载 URL 继续保持为 ModelScope 单文件地址
- 路径仍为 `<username>/whisper_model.tflite`
- 下载成功后，将当前 `version_tag` 写入本地 SharedPreferences

## Publishing Contract

发布一个新版本时，需要同时更新：

1. `hanxiaofeng218/CareSpeech-ASR/<username>/whisper_model.tflite`
2. `hanxiaofeng218/CareSpeech-ASR/<username>/version.json`

其中 `version.json` 的 `version_tag` 必须变更，否则客户端不会识别为新版本。
