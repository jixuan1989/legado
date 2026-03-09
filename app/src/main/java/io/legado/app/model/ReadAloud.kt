package io.legado.app.model

import android.content.Context
import android.content.Intent
import android.os.Bundle
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.IntentAction
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.help.config.AppConfig
import io.legado.app.help.book.isEpub
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.model.localBook.EpubFile
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.EpubAudioReadAloudService
import io.legado.app.service.HttpReadAloudService
import io.legado.app.service.TTSReadAloudService
import io.legado.app.utils.GSON
import io.legado.app.utils.LogUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.postEvent
import io.legado.app.utils.startForegroundServiceCompat
import io.legado.app.utils.toastOnUi
import splitties.init.appCtx

object ReadAloud {
    const val BOOK_BUILT_IN_MEDIA = "__book_built_in_media__"

    private var aloudClass: Class<*>
        get() = ReadAloudServiceTarget.get()
        set(value) = ReadAloudServiceTarget.set(value)
    private var pendingTouchSeekRatio: Float? = null
    val ttsEngine get() = ReadBook.book?.getTtsEngine() ?: AppConfig.ttsEngine
    var httpTTS: HttpTTS? = null

    init {
        aloudClass = getReadAloudClass()
    }

    private val selectedEngine: SelectItem<String>?
        get() = GSON.fromJsonObject<SelectItem<String>>(ttsEngine).getOrNull()

    private fun canUseBookBuiltInMedia(): Boolean {
        val book = ReadBook.book ?: return false
        if (!book.isEpub) return false
        val chapter = appDb.bookChapterDao.getChapter(book.bookUrl, ReadBook.durChapterIndex)
            ?: return EpubFile.hasAudio(book)
        return EpubFile.hasAudio(book, chapter)
    }

    fun useBookBuiltInMedia(): Boolean {
        return selectedEngine?.value == BOOK_BUILT_IN_MEDIA
    }

    fun willUseBookBuiltInMedia(): Boolean {
        return getReadAloudClass() == EpubAudioReadAloudService::class.java
    }

    fun setTouchSeekRatio(ratio: Float?) {
        pendingTouchSeekRatio = ratio?.coerceIn(0f, 1f)
    }

    fun consumeTouchSeekRatio(): Float? {
        val ratio = pendingTouchSeekRatio
        pendingTouchSeekRatio = null
        return ratio
    }

    fun getTtsEngineName(): String? {
        val ttsEngine = ttsEngine ?: return null
        if (StringUtils.isNumeric(ttsEngine)) return null
        val engine = selectedEngine?.value ?: ttsEngine
        return engine.takeUnless {
            it.isBlank() || it == BOOK_BUILT_IN_MEDIA
        }
    }

    private fun getReadAloudClass(): Class<*> {
        val ttsEngine = ttsEngine
        val canUseBuiltInMedia = canUseBookBuiltInMedia()
        if (ttsEngine.isNullOrBlank()) {
            return if (canUseBuiltInMedia) {
                EpubAudioReadAloudService::class.java
            } else {
                TTSReadAloudService::class.java
            }
        }
        if (StringUtils.isNumeric(ttsEngine)) {
            httpTTS = appDb.httpTTSDao.get(ttsEngine.toLong())
            if (httpTTS != null) {
                return HttpReadAloudService::class.java
            }
        }
        if (useBookBuiltInMedia()) {
            return if (canUseBuiltInMedia) {
                EpubAudioReadAloudService::class.java
            } else {
                TTSReadAloudService::class.java
            }
        }
        return TTSReadAloudService::class.java
    }

    fun upReadAloudClass() {
        stop(appCtx)
        aloudClass = getReadAloudClass()
    }

    internal fun switchRunningService(serviceClass: Class<*>) {
        aloudClass = serviceClass
    }

    fun play(
        context: Context,
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0
    ) {
        val targetClass = getReadAloudClass()
        if (BaseReadAloudService.isRun && aloudClass != targetClass) {
            context.startForegroundServiceCompat(Intent(context, aloudClass).apply {
                action = IntentAction.stop
            })
        }
        aloudClass = targetClass
        val intent = Intent(context, aloudClass)
        intent.action = IntentAction.play
        intent.putExtra("play", play)
        intent.putExtra("pageIndex", pageIndex)
        intent.putExtra("startPos", startPos)
        LogUtils.d("ReadAloud", intent.toString())
        try {
            context.startForegroundServiceCompat(intent)
        } catch (e: Exception) {
            val msg = "启动朗读服务出错\n${e.localizedMessage}"
            AppLog.put(msg, e)
            context.toastOnUi(msg)
        }
    }

    fun playByEventBus(
        play: Boolean = true,
        pageIndex: Int = ReadBook.durPageIndex,
        startPos: Int = 0
    ) {
        val bundle = Bundle().apply {
            putBoolean("play", play)
            putInt("pageIndex", pageIndex)
            putInt("startPos", startPos)
        }
        postEvent(EventBus.READ_ALOUD_PLAY, bundle)
    }

    fun pause(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.pause
            context.startForegroundServiceCompat(intent)
        }
    }

    fun resume(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.resume
            context.startForegroundServiceCompat(intent)
        }
    }

    fun stop(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.stop
            context.startForegroundServiceCompat(intent)
        }
    }

    fun prevParagraph(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.prevParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun nextParagraph(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.nextParagraph
            context.startForegroundServiceCompat(intent)
        }
    }

    fun upTtsSpeechRate(context: Context) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.upTtsSpeechRate
            context.startForegroundServiceCompat(intent)
        }
    }

    fun setTimer(context: Context, minute: Int) {
        if (BaseReadAloudService.isRun) {
            val intent = Intent(context, aloudClass)
            intent.action = IntentAction.setTimer
            intent.putExtra("minute", minute)
            context.startForegroundServiceCompat(intent)
        }
    }

}
