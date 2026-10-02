package com.example.annotator.storage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Debounces writes by 500 ms after the last change, with an immediate [flush] path for
 * image change / onPause / export (SPEC section 7).
 */
class Autosave(private val scope: CoroutineScope, private val debounceMillis: Long = 500) {

    private var pendingJob: Job? = null
    private var pendingWrite: (() -> Unit)? = null

    fun schedule(write: () -> Unit) {
        pendingWrite = write
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(debounceMillis)
            flush()
        }
    }

    /** Writes immediately if there is a pending change, and cancels the debounce timer. */
    fun flush() {
        pendingJob?.cancel()
        pendingJob = null
        val write = pendingWrite
        pendingWrite = null
        write?.invoke()
    }
}
