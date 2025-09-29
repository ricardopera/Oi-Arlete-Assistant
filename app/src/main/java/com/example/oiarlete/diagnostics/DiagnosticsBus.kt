package com.example.oiarlete.diagnostics

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

object DiagnosticsBus {
    sealed class Event {
        data class MicLevel(val rms: Float): Event()
        data class WakeDetected(val source: String): Event()
        data class EngineStatus(val porcupineLoaded: Boolean, val voskLoaded: Boolean): Event()
        data class SpeechResult(val text: String): Event()
        data class Error(val message: String): Event()
        data class Info(val message: String): Event()
    }

    private val _events = MutableSharedFlow<Event>(extraBufferCapacity = 64)
    val events: SharedFlow<Event> = _events

    fun emit(e: Event) { _events.tryEmit(e) }
}
