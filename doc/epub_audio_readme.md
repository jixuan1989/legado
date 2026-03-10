# EPUB 内置音频朗读

## 功能说明

- 朗读 EPUB 时，若该 EPUB 内含有内置音频（mp3/ogg/mp4），**优先使用内置音频**按章播放，不再使用 TTS。
- 无内置音频或非 EPUB 时，仍使用原有 TTS 朗读。

## 实现要点

- **EpubFile**：增加 `hasAudio(book)`、`getAudioFile(book, chapter)`，以及纯函数 `deriveAudioHrefFromChapterUrl`（供单测使用）。
- **EpubAudioReadAloudService**：新建朗读服务，用 ExoPlayer 播放从 EPUB 中解压出的音频文件，支持暂停/继续/上一章/下一章。
- **ReadAloud**：在 `getReadAloudClass()` 中，当 `ttsEngine` 为空且当前书为本地 EPUB 且 `EpubFile.hasAudio(book)` 为真时，选用 `EpubAudioReadAloudService`，否则仍为 TTS。

## 测试

- 单测：`app/src/test/java/io/legado/app/model/localBook/EpubFileTest.kt` 覆盖 `deriveAudioHrefFromChapterUrl`。
- 带音频的 EPUB 测试文件：`testdata/sample_第1卷.epub`。

## 编译可安装 APK

版本号已加后缀 `.100`（在 `app/build.gradle` 中：`version = "3." + releaseTime() + ".100"`）。

在项目根目录执行：

```bash
./gradlew :app:assembleAppDebug
```

产出路径：`app/build/outputs/apk/app/debug/legado_app_3.xx.xxxxxx.100-debug.apk`。

如需 release 包（需配置签名）：

```bash
./gradlew :app:assembleAppRelease
```
