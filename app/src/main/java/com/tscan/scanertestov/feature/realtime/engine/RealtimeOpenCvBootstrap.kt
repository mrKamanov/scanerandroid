package com.tscan.scanertestov.feature.realtime.engine

/**
 * Описание: однократная инициализация OpenCV для быстрой проверки.
 */
import org.opencv.android.OpenCVLoader

internal object RealtimeOpenCvBootstrap {
    @Volatile
    private var ready: Boolean = false

    fun ensureLoaded(): Boolean {
        if (ready) return true
        synchronized(this) {
            if (ready) return true
            ready = OpenCVLoader.initLocal()
            return ready
        }
    }
}
