package io.legado.app.model

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.legado.app.constant.IntentAction
import io.legado.app.service.BaseReadAloudService
import io.legado.app.service.EpubAudioReadAloudService
import io.legado.app.service.TTSReadAloudService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReadAloudRoutingIntegrationTest {

    private lateinit var context: RecordingContext

    @Before
    fun setUp() {
        context = RecordingContext(ApplicationProvider.getApplicationContext())
        setReadAloudRunning(true)
    }

    @After
    fun tearDown() {
        setReadAloudRunning(false)
        ReadAloud.switchRunningService(TTSReadAloudService::class.java)
    }

    @Test
    fun pause_should_route_to_latest_switched_service() {
        ReadAloud.switchRunningService(EpubAudioReadAloudService::class.java)
        ReadAloud.switchRunningService(TTSReadAloudService::class.java)

        ReadAloud.pause(context)

        val intent = context.lastStartedIntent
        assertNotNull(intent)
        assertEquals(TTSReadAloudService::class.java.name, intent?.component?.className)
        assertEquals(IntentAction.pause, intent?.action)
    }

    @Test
    fun setTimer_should_route_to_latest_switched_service() {
        ReadAloud.switchRunningService(EpubAudioReadAloudService::class.java)
        ReadAloud.switchRunningService(TTSReadAloudService::class.java)

        ReadAloud.setTimer(context, 15)

        val intent = context.lastStartedIntent
        assertNotNull(intent)
        assertEquals(TTSReadAloudService::class.java.name, intent?.component?.className)
        assertEquals(IntentAction.setTimer, intent?.action)
        assertEquals(15, intent?.getIntExtra("minute", -1))
    }

    private fun setReadAloudRunning(value: Boolean) {
        val field = BaseReadAloudService::class.java.getDeclaredField("isRun")
        field.isAccessible = true
        field.setBoolean(null, value)
    }

    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var lastStartedIntent: Intent? = null

        override fun startService(service: Intent): ComponentName? {
            lastStartedIntent = Intent(service)
            val className = service.component?.className ?: "unknown"
            return ComponentName(packageName, className)
        }
    }
}
