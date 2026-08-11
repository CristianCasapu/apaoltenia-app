package ro.apaoltenia.client

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Porneste / opreste verificarea periodica a facturilor. */
object InvoiceCheckScheduler {

    private const val WORK_NAME = "invoice_check"

    fun enable(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<InvoiceCheckWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        // UPDATE (nu KEEP): schimbarile viitoare de interval/constrangeri se
        // aplica si instalarilor existente, iar re-armarea din App.onCreate e
        // sigura (nu reseteaza perioada).
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
