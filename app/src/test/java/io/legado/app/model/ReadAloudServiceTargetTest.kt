package io.legado.app.model

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ReadAloudServiceTargetTest {

    private class FirstService
    private class SecondService

    @Before
    fun setUp() {
        ReadAloudServiceTarget.set(FirstService::class.java)
    }

    @Test
    fun `set should update current service class`() {
        ReadAloudServiceTarget.set(SecondService::class.java)

        assertEquals(SecondService::class.java, ReadAloudServiceTarget.get())
    }

    @Test
    fun `set should overwrite previous service class`() {
        ReadAloudServiceTarget.set(SecondService::class.java)
        ReadAloudServiceTarget.set(FirstService::class.java)

        assertEquals(FirstService::class.java, ReadAloudServiceTarget.get())
    }
}
