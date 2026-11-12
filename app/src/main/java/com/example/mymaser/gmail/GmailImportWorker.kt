package com.example.mymaser.gmail

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mymaser.ai.DocumentImporter
import com.example.mymaser.ai.GeminiAiClient
import com.example.mymaser.dataStore
import com.example.mymaser.history.HistoryDb
import com.example.mymaser.reminders.postEmailImports
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.aiEnabledFlow
import com.example.mymaser.settings.displayCurrencyFlow
import com.example.mymaser.settings.gmailSettingsFlow
import com.example.mymaser.settings.maserBasisFlow
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Once a day, reads new payslips and receipts from Gmail into entries waiting for review. */
class GmailImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** The daily and check-now runs can overlap; one at a time keeps each email read once. */
    override suspend fun doWork(): Result = runLock.withLock { importNewEmails() }

    private suspend fun importNewEmails(): Result {
        val context = applicationContext
        val settings = context.gmailSettingsFlow().first()
        val aiEnabled = context.aiEnabledFlow().first()
        if (!settings.connected || !aiEnabled) return Result.success()

        val token = try {
            silentGmailToken(context)
        } catch (_: IOException) {
            return Result.retry()
        } ?: return needsReconnect(context)

        val db = HistoryDb.getInstance(context)
        val importer = GmailImporter(
            db.getPendingImportDao(),
            DocumentImporter(GeminiAiClient.getInstance(context)) { context.aiEnabledFlow().first() }
        )
        return try {
            val found = importer.run(
                source = GmailApi(token),
                query = buildGmailQuery(settings.keywords),
                basis = context.maserBasisFlow().first(),
                fallbackCurrency = context.displayCurrencyFlow().first(),
                knownNames = db.getHistoryDao().getAll().map { it.name.trim() }.distinct(),
                nowMillis = System.currentTimeMillis()
            )
            context.dataStore.edit { it[SettingsKeys.GMAIL_NEEDS_RECONNECT] = false }
            if (found > 0) postEmailImports(context, found)
            Result.success()
        } catch (_: GmailUnauthorizedException) {
            clearGmailToken(context, token)
            if (runAttemptCount < MAX_AUTH_RETRIES) Result.retry() else needsReconnect(context)
        } catch (_: IOException) {
            Result.retry()
        }
    }

    private suspend fun needsReconnect(context: Context): Result {
        context.dataStore.edit { it[SettingsKeys.GMAIL_NEEDS_RECONNECT] = true }
        return Result.success()
    }

    private companion object {
        const val MAX_AUTH_RETRIES = 2
        val runLock = Mutex()
    }
}

object GmailScheduler {
    private const val DAILY = "gmail_import_daily"
    private const val NOW = "gmail_import_now"

    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Runs the daily check only while Gmail is connected and smart import is on. */
    suspend fun sync(context: Context) {
        val workManager = WorkManager.getInstance(context)
        val wanted = context.gmailSettingsFlow().first().connected && context.aiEnabledFlow().first()
        if (!wanted) {
            workManager.cancelUniqueWork(DAILY)
            return
        }
        workManager.enqueueUniquePeriodicWork(
            DAILY,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<GmailImportWorker>(1, TimeUnit.DAYS).setConstraints(online).build()
        )
    }

    fun runNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<GmailImportWorker>().setConstraints(online).build()
        )
    }
}
