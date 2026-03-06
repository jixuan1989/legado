package io.legado.app.service

import android.app.PendingIntent
import android.net.Uri
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.extractor.DefaultExtractorsFactory
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.help.MediaHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.model.ReadBook
import io.legado.app.model.localBook.EpubFile
import io.legado.app.utils.servicePendingIntent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers.Main
import io.legado.app.utils.postEvent

/**
 * 朗读服务：当 EPUB 内含音频时优先播放内置音频（按章播放），否则不在此服务处理。
 * 由 ReadAloud 在 TTS 前根据 EpubFile.hasAudio(book) 选择本服务。
 */
class EpubAudioReadAloudService : BaseReadAloudService(), Player.Listener {

    private var exoPlayer: ExoPlayer? = null
    private var progressJob: Job? = null
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
            nextChapter()
            return
        }
        super.play()
        MediaHelp.playSilentSound(this)
        progressJob?.cancel()
        exoPlayer?.let { player ->
            player.stop()
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(Uri.fromFile(audioFile)))
            player.prepare()
            player.playWhenReady = true
            val startPos = (ReadBook.durChapterPos).toLong().coerceAtLeast(0)
            if (startPos > 0) player.seekTo(startPos)
            progressJob = lifecycleScope.launch(Main) {
                while (isActive) {
                    delay(1000)
                    if (!pause) {
                        ReadBook.durChapterPos = player.currentPosition.toInt()
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
                        ReadBook.durChapterPos = player.currentPosition.toInt()
                    }
                }
            }
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> {
                progressJob?.cancel()
                exoPlayer?.currentPosition?.let { ReadBook.durChapterPos = it.toInt() }
                nextChapter()
            }
            Player.STATE_READY -> {
                postEvent(EventBus.ALOUD_STATE, io.legado.app.constant.Status.PLAY)
            }
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        AppLog.put("EPUB 音频播放出错\n${error.message}", error)
        toastOnUi(getString(R.string.epub_audio_play_error))
        nextChapter()
    }

    override fun aloudServicePendingIntent(actionStr: String): PendingIntent? {
        return servicePendingIntent<EpubAudioReadAloudService>(actionStr)
    }
}
