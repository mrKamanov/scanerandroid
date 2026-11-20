package com.tscan.scanertestov

/**
 * Описание: Application — инициализация файловых хранилищ результатов и отчётов.
 * Чтение с диска выполняется в фоне заранее, чтобы экраны открывались без задержки.
 */
import android.app.Application
import com.tscan.scanertestov.feature.batch.BatchLastRunStore
import com.tscan.scanertestov.feature.reports.ReportsSelectedWorksStore

class ScanerTestovApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        BatchLastRunStore.init(this)
        ReportsSelectedWorksStore.init(this)
        Thread({
            runCatching { ReportsSelectedWorksStore.ensureLoaded() }
            runCatching { BatchLastRunStore.ensureLoaded() }
        }, "stores-preload").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }
    }
}
