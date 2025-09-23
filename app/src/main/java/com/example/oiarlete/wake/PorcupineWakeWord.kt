package com.example.oiarlete.wake

/**
 * Futuro wrapper para Porcupine.
 * Mantém a mesma API do stub atual: start/stop e setOnWakeListener.
 */
class PorcupineWakeWord {
    private var listener: (() -> Unit)? = null

    fun setOnWakeListener(l: (() -> Unit)?) { listener = l }
    fun start() { /* TODO Porcupine init/loop */ }
    fun stop() { /* TODO shutdown */ }
}
