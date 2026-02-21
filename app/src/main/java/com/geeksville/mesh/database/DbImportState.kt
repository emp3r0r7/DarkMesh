package com.geeksville.mesh.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

object DbImportState {

    val dbImportContactMap = ConcurrentHashMap<Int, String>()
    private var startedAt = 0L

    const val NODE_EXPORT_SEPARATOR: String = "☠"
    const val NODE_EXPORT_DB_VER: Float = 0.1f
    const val MAX_IMPORTED_NODES_PERMITTED = 250
    const val ARGS_TAG = "ARGS|"
    const val ARG_FAVORITE_ONLY = 1
    const val MAX_ALLOWED_DB_SIZE_BYTES = 1L * 1024L * 1024L // 1 MB

    private const val JOB_TIMEOUT_MS = 20_000L
    private var currentImportId = 0L
    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress: StateFlow<String?> = _importProgress.asStateFlow()

    private val _importComplete = MutableStateFlow<Long?>(null)
    val importComplete: StateFlow<Long?> = _importComplete.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timeoutJob: Job? = null

    fun emitFirst(){
        dbImportContactMap.clear()
        currentImportId = System.currentTimeMillis()
        startedAt = System.currentTimeMillis()
        _importComplete.value = null
        emitImportProgress("Preparing..")
    }

    fun emitImportProgress(contact: String) {
        val importIdAtStart = currentImportId

        _importProgress.value = contact

        timeoutJob?.cancel()

        timeoutJob = scope.launch {
            delay(JOB_TIMEOUT_MS)
            if (importIdAtStart == currentImportId) {
                _importProgress.value = null
                _importComplete.value = System.currentTimeMillis()
                dbImportContactMap.clear()
            }
        }
    }

    fun setImportCompleteNull(){
        _importComplete.value = null
    }

    fun interruptRunningImport(){
        currentImportId++
        _importProgress.value = null
        _importComplete.value = System.currentTimeMillis()
        dbImportContactMap.clear()
    }

    fun importInProgress() : Boolean {
        return _importProgress.value != null && _importProgress.value != ""
    }

    fun elapsed() : Long? {
       return _importComplete.value?.minus(startedAt)
    }

}
