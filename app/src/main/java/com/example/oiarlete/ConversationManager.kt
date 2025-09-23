package com.example.oiarlete

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ConversationManager(
    private val listeningTimeoutSeconds: Int = 15,
    private val speakingTimeoutSeconds: Int = 30,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {
    enum class State { Idle, ActiveListening, Speaking }

    private var timeoutJob: Job? = null

    private var listener: ((State) -> Unit)? = null
    private val _stateFlow: MutableStateFlow<State> = MutableStateFlow(State.Idle)
    val stateFlow: StateFlow<State> get() = _stateFlow

    var state: State = State.Idle
        private set(value) {
            field = value
            listener?.invoke(value)
            _stateFlow.value = value
        }

    fun setOnStateChangeListener(l: ((State) -> Unit)?) { listener = l }

    fun onWakeWord() {
        state = State.ActiveListening
        scheduleTimeoutFor(State.ActiveListening)
    }
    fun onRecognizedIntent() {
        state = State.Speaking
        scheduleTimeoutFor(State.Speaking)
    }
    fun onTtsComplete() {
        state = State.ActiveListening
        scheduleTimeoutFor(State.ActiveListening)
    }
    fun onStop() {
        state = State.Idle
        cancelTimeout()
    }
    fun onTimeout() {
        state = State.Idle
        cancelTimeout()
    }

    private fun scheduleTimeoutFor(state: State) {
        cancelTimeout()
        val seconds = when (state) {
            State.ActiveListening -> listeningTimeoutSeconds
            State.Speaking -> speakingTimeoutSeconds
            State.Idle -> 0
        }
        if (seconds <= 0) return
        timeoutJob = scope.launch {
            delay(seconds * 1000L)
            onTimeout()
        }
    }

    private fun cancelTimeout() {
        try { timeoutJob?.cancel() } catch (_: Exception) {} finally { timeoutJob = null }
    }
}
