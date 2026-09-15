package com.jarvis.assistant.core.conversation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class TaskRecord(val goal: String, val status: String, val time: Long = System.currentTimeMillis())

/** In-memory execution log (#5 task history / execution logs). */
object TaskLog {
    private val _records = MutableStateFlow<List<TaskRecord>>(emptyList())
    val records: StateFlow<List<TaskRecord>> = _records

    fun add(goal: String, status: String) {
        _records.value = (_records.value + TaskRecord(goal, status)).takeLast(100)
    }

    fun clear() { _records.value = emptyList() }
}
