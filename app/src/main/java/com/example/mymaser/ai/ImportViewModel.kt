package com.example.mymaser.ai

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.mymaser.history.HistoryDb
import com.example.mymaser.history.HistoryRepository
import com.example.mymaser.settings.aiEnabledFlow
import com.example.mymaser.settings.displayCurrencyFlow
import com.example.mymaser.settings.maserBasisFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ImportState {
    data object Idle : ImportState
    data object Reading : ImportState
    data class Review(val entries: List<ExtractedEntry>, val skipped: Int) : ImportState
    data class Empty(val skipped: Int) : ImportState
    data class Failed(val error: AiError) : ImportState
}

val AiError.canRetry: Boolean
    get() = this == AiError.Network || this == AiError.Failed || this == AiError.QuotaExceeded

/** Reads shared, scanned or picked documents into entries for the review sheet. */
class ImportViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val history = HistoryRepository(HistoryDb.getInstance(application).getHistoryDao())
    private val importer = DocumentImporter(GeminiAiClient.getInstance(application)) {
        application.aiEnabledFlow().first()
    }

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    val aiEnabled: StateFlow<Boolean> = application.aiEnabledFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), false)

    /** Kept only while a retryable failure is on screen. */
    private var retryDocuments: ReadDocuments? = null
    private var job: Job? = null

    fun readShare(intent: Intent) = start { readShare(app, intent) }

    fun readUris(uris: List<Uri>) {
        if (uris.isNotEmpty()) start { readFiles(app, uris) }
    }

    fun retry() {
        val documents = retryDocuments ?: return
        start { documents }
    }

    fun dismiss() {
        job?.cancel()
        retryDocuments = null
        _state.value = ImportState.Idle
    }

    private fun start(load: suspend () -> ReadDocuments) {
        job?.cancel()
        retryDocuments = null
        _state.value = ImportState.Reading
        job = viewModelScope.launch {
            val documents = load()
            if (documents.inputs.isEmpty()) {
                _state.value = ImportState.Empty(documents.skipped)
                return@launch
            }
            val result = importer.read(
                inputs = documents.inputs,
                basis = app.maserBasisFlow().first(),
                fallbackCurrency = app.displayCurrencyFlow().first(),
                knownNames = history.observeHistory().first().map { it.name }.distinct()
            )
            _state.value = when (result) {
                is AiResult.Error -> {
                    if (result.reason.canRetry) retryDocuments = documents
                    ImportState.Failed(result.reason)
                }
                is AiResult.Success ->
                    if (result.value.isEmpty()) ImportState.Empty(documents.skipped)
                    else ImportState.Review(result.value, documents.skipped)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
