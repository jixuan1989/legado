package io.legado.app.model

import io.legado.app.service.TTSReadAloudService
import java.util.concurrent.atomic.AtomicReference

internal object ReadAloudServiceTarget {

    private val serviceClassRef = AtomicReference<Class<*>>(TTSReadAloudService::class.java)

    fun get(): Class<*> = serviceClassRef.get()

    fun set(serviceClass: Class<*>) {
        serviceClassRef.set(serviceClass)
    }
}
