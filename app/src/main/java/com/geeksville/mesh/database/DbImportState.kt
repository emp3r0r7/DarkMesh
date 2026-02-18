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
    const val MAX_IMPORTED_NODES_PERMITTED = 255
    private const val JOB_TIMEOUT_MS = 20_000L

    private val _importProgress = MutableStateFlow<String?>(null)
    val importProgress: StateFlow<String?> = _importProgress.asStateFlow()

    private val _importComplete = MutableStateFlow<Long?>(null)
    val importComplete: StateFlow<Long?> = _importComplete.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var timeoutJob: Job? = null

    fun emitFirst(){
        startedAt = System.currentTimeMillis()
        _importComplete.value = null
        emitImportProgress("Preparing..")
    }

    fun emitImportProgress(contact: String) {
        _importProgress.value = contact

        timeoutJob?.cancel()

        timeoutJob = scope.launch {
            delay(JOB_TIMEOUT_MS)
            _importProgress.value = null
            _importComplete.value = System.currentTimeMillis()
        }
    }

    fun importInProgress() : Boolean {
        return _importProgress.value != null && _importProgress.value != ""
    }

    fun elapsed() : Long? {
       return _importComplete.value?.minus(startedAt)
    }

}
