package com.example.mymaser.gmail

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.mymaser.ai.DocumentImporter
import com.example.mymaser.ai.GeminiAiClient
import com.example.mymaser.ai.LockedPdfOpener
import com.example.mymaser.ai.knownNamesOf
import com.example.mymaser.dataStore
import com.example.mymaser.history.HistoryDb
import com.example.mymaser.reminders.postEmailImports
import com.example.mymaser.settings.GmailOutcome
import com.example.mymaser.settings.SettingsKeys
import com.example.mymaser.settings.aiEnabledFlow
import com.example.mymaser.settings.displayCurrencyFlow
import com.example.mymaser.settings.gmailSettingsFlow
import com.example.mymaser.settings.maserBasisFlow
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Once a day, reads new payslips and receipts from Gmail into entries waiting for review. */
class GmailImportWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    /** The daily and check-now runs can overlap; one at a time keeps each email read once. */
    override suspend fun doWork(): Result = runLock.withLock { importNewEmails() }

    private suspend fun importNewEmails(): Result {
        val context = applicationContext
        val started = System.currentTimeMillis()
        Log.i(TAG, "Check started ($trigger, attempt ${runAttemptCount + 1})")
        val settings = context.gmailSettingsFlow().first()
        val aiEnabled = context.aiEnabledFlow().first()
        if (!settings.connected || !aiEnabled) {
            Log.i(TAG, "Skipped: connected=${settings.connected} aiEnabled=$aiEnabled")
            return Result.success()
        }

        val token = try {
            silentGmailToken(context)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't get a Gmail token, will retry", e)
            return failed(context)
        } ?: run {
            Log.w(TAG, "Gmail permission needs the user again")
            return needsReconnect(context)
        }

        val db = HistoryDb.getInstance(context)
        val importer = GmailImporter(
            db.getPendingImportDao(),
            DocumentImporter(GeminiAiClient.getInstance(context)) { context.aiEnabledFlow().first() },
            unlock = LockedPdfOpener(context)::open,
            log = { Log.i(TAG, it) }
        )
        return try {
            val run = importer.run(
                source = GmailApi(token),
                query = buildGmailQuery(settings.keywords),
                basis = context.maserBasisFlow().first(),
                fallbackCurrency = context.displayCurrencyFlow().first(),
                knownNames = knownNamesOf(db.getHistoryDao().getAll()),
                nowMillis = System.currentTimeMillis()
            )
            val outcome = when {
                run.found > 0 -> GmailOutcome.Found
                run.stoppedBy != null -> GmailOutcome.AiUnavailable
                else -> GmailOutcome.NothingNew
            }
            Log.i(TAG, "Check finished in ${System.currentTimeMillis() - started} ms: $outcome, found ${run.found}")
            record(context, outcome, run.found) { it[SettingsKeys.GMAIL_NEEDS_RECONNECT] = false }
            if (run.found > 0) postEmailImports(context, run.found)
            Result.success()
        } catch (e: GmailUnauthorizedException) {
            Log.w(TAG, "Gmail rejected the token (attempt ${runAttemptCount + 1})", e)
            clearGmailToken(context, token)
            if (runAttemptCount < MAX_AUTH_RETRIES) Result.retry() else needsReconnect(context)
        } catch (e: IOException) {
            Log.w(TAG, "Check failed, will retry", e)
            failed(context)
        }
    }

    private suspend fun failed(context: Context): Result {
        record(context, GmailOutcome.Failed, found = 0)
        return Result.retry()
    }

    private suspend fun needsReconnect(context: Context): Result {
        context.dataStore.edit { it[SettingsKeys.GMAIL_NEEDS_RECONNECT] = true }
        return Result.success()
    }

    private suspend fun record(
        context: Context,
        outcome: GmailOutcome,
        found: Int,
        also: (MutablePreferences) -> Unit = {}
    ) {
        context.dataStore.edit {
            it[SettingsKeys.GMAIL_LAST_CHECK_AT] = System.currentTimeMillis()
            it[SettingsKeys.GMAIL_LAST_OUTCOME] = outcome.name
            it[SettingsKeys.GMAIL_LAST_FOUND] = found
            also(it)
        }
    }

    private val trigger get() = if (GmailScheduler.NOW in tags) "check now" else "daily"

    private companion object {
        const val MAX_AUTH_RETRIES = 2
        val runLock = Mutex()
    }
}

/** Filter logcat with `-s MyMaserGmail` to follow each check. */
private const val TAG = "MyMaserGmail"

/** Whether a check is going on right now, as Settings shows it. */
enum class GmailProgress {
    Idle,

    /** Check now was tapped and waits for a connection. */
    Waiting,

    /** Check now failed once and waits to try again. */
    Retrying,
    Running
}

object GmailScheduler {
    private const val DAILY = "gmail_import_daily"
    internal const val NOW = "gmail_import_now"

    fun progress(context: Context): Flow<GmailProgress> {
        val workManager = WorkManager.getInstance(context)
        return combine(
            workManager.getWorkInfosForUniqueWorkFlow(NOW),
            workManager.getWorkInfosForUniqueWorkFlow(DAILY)
        ) { now, daily ->
            val waiting = now.filter { it.state == WorkInfo.State.ENQUEUED }
            when {
                (now + daily).any { it.state == WorkInfo.State.RUNNING } -> GmailProgress.Running
                waiting.any { it.runAttemptCount > 0 } -> GmailProgress.Retrying
                waiting.isNotEmpty() -> GmailProgress.Waiting
                else -> GmailProgress.Idle
            }
        }.distinctUntilChanged()
    }

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
            OneTimeWorkRequestBuilder<GmailImportWorker>().setConstraints(online).addTag(NOW).build()
        )
    }
}
