package io.legado.app.service

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.help.MediaHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadAloud
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.EpubFile
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToLong
import kotlinx.coroutines.Dispatchers.Main
import io.legado.app.utils.postEvent

/**
 * 朗读服务：当 EPUB 内含音频时优先播放内置音频（按章播放），否则不在此服务处理。
 * 由 ReadAloud 在 TTS 前根据 EpubFile.hasAudio(book) 选择本服务。
 */
class EpubAudioReadAloudService : BaseReadAloudService(), Player.Listener {

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
    private var pendingSeekRatio: Float? = null
    private val TAG = "EpubAudioReadAloud"

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(this, DefaultExtractorsFactory())
                    .setDataSourceFactory(DefaultDataSource.Factory(this))
            )
            .build()
            .also { it.addListener(this) }
    }

    override fun onDestroy() {
        progressJob?.cancel()
        exoPlayer?.removeListener(this)
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
    }

    @Synchronized
    override fun play() {
        if (!requestFocus()) return
        val book = ReadBook.book ?: return
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
            ?: return
        val audioFile = EpubFile.getAudioFile(book, chapter)
        if (audioFile == null || !audioFile.exists()) {
            toastOnUi(getString(R.string.epub_audio_not_found))
            switchToTtsFallback()
            return
        }
        super.play()
        MediaHelp.playSilentSound(this)
        progressJob?.cancel()
        exoPlayer?.let { player ->
            player.stop()
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(audioFile)))
            pendingSeekRatio = buildSeekRatio()
            player.prepare()
            applyPendingSeek(player)
            player.playWhenReady = true
            progressJob = lifecycleScope.launch(Main) {
                while (isActive) {
                    delay(1000)
                    if (!pause) {
                        updateReadAloudPosition(player)
                    }
                }
            }
        }
    }

    override fun playStop() {
        progressJob?.cancel()
        exoPlayer?.stop()
    }

    override fun upSpeechRate(reset: Boolean) {
        if (!reset) {
            val rate = (AppConfig.ttsSpeechRate + 5) / 10f
            exoPlayer?.setPlaybackSpeed(rate)
        }
    }

    override fun pauseReadAloud(abandonFocus: Boolean) {
        super.pauseReadAloud(abandonFocus)
        progressJob?.cancel()
        exoPlayer?.pause()
    }

    override fun resumeReadAloud() {
        super.resumeReadAloud()
        progressJob?.cancel()
        exoPlayer?.play()
        exoPlayer?.let { player ->
            progressJob = lifecycleScope.launch(Main) {
                while (isActive) {
                    delay(1000)
                    if (!pause) {
                        updateReadAloudPosition(player)
                    }
                }
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> {
                progressJob?.cancel()
                ReadBook.durChapterPos = currentChapterLength()
                nextChapter()
            }
            Player.STATE_READY -> {
                exoPlayer?.let { applyPendingSeek(it) }
                postEvent(EventBus.ALOUD_STATE, io.legado.app.constant.Status.PLAY)
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        AppLog.put("EPUB 音频播放出错\n${error.message}", error)
        toastOnUi(getString(R.string.epub_audio_play_error))
        switchToTtsFallback()
    }

    private fun currentChapterLength(): Int {
        return textChapter?.chapterLength ?: ReadBook.curTextChapter?.chapterLength ?: 0
    }

    private fun buildSeekRatio(): Float? {
        ReadAloud.consumeTouchSeekRatio()?.let { return it }
        textChapter?.getVisualProgressRatio(readAloudNumber)?.let { return it }
        val chapterLength = currentChapterLength()
        if (chapterLength <= 0) return null
        return (readAloudNumber.toFloat() / chapterLength.toFloat()).coerceIn(0f, 1f)
    }

    private fun applyPendingSeek(player: ExoPlayer) {
        val ratio = pendingSeekRatio ?: return
        val duration = player.duration
        if (duration > 0) {
            player.seekTo((duration * ratio).roundToLong())
            pendingSeekRatio = null
        }
    }

    private fun updateReadAloudPosition(player: ExoPlayer) {
        val duration = player.duration
        val chapterLength = currentChapterLength()
        ReadBook.durChapterPos = if (duration > 0 && chapterLength > 0) {
            val ratio = (player.currentPosition.toDouble() / duration.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
            textChapter?.getChapterPositionByVisualRatio(ratio)
                ?: ((ratio * chapterLength.toFloat()).toInt().coerceIn(0, chapterLength))
        } else {
            player.currentPosition.toInt()
        }
        upTtsProgress(ReadBook.durChapterPos + 1)
    }

    private fun switchToTtsFallback() {
        progressJob?.cancel()
        exoPlayer?.stop()
        ReadAloud.switchRunningService(TTSReadAloudService::class.java)
        val pageIndex = ReadBook.durPageIndex
        val pageStart = textChapter?.getReadLength(pageIndex) ?: 0
        val startPos = (ReadBook.durChapterPos - pageStart).coerceAtLeast(0)
        val intent = Intent(this, TTSReadAloudService::class.java).apply {
            action = IntentAction.play
            putExtra("play", true)
            putExtra("pageIndex", pageIndex)
            putExtra("startPos", startPos)
        }
        stopSelf()
        startForegroundServiceCompat(intent)
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<EpubAudioReadAloudService>(actionStr)
    }
}
