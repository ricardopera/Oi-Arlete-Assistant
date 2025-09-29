package com.example.oiarlete.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

object AudioInputSelector {
    data class Choice(val device: AudioDeviceInfo?, val reason: String)

    fun findPreferredInput(context: Context): Choice {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val inputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            val usb = inputs.firstOrNull { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
            if (usb != null) Choice(usb, "USB") else {
                // Apenas USB - não usar built-in mic devido a problemas de AudioFlinger
                Choice(null, "NO_USB_FOUND")
            }
        } catch (_: Exception) {
            Choice(null, "ERROR")
        }
    }

    fun listInputs(context: Context): List<AudioDeviceInfo> {
        return try {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val allInputs = am.getDevices(AudioManager.GET_DEVICES_INPUTS)
            // Filtrar apenas dispositivos USB para evitar problemas com AudioFlinger
            allInputs.filter { it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET }
        } catch (_: Exception) { emptyList() }
    }

    fun findById(context: Context, id: Int): AudioDeviceInfo? {
        if (id <= 0) return null
        return listInputs(context).firstOrNull { it.id == id }
    }

    fun describe(device: AudioDeviceInfo?): String {
        if (device == null) return "(default)"
        val typeName = when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_MIC -> "BUILTIN_MIC"
            AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
            AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BT_SCO"
            else -> device.type.toString()
        }
        return "$typeName id=${device.id} chan=${device.channelCounts?.joinToString()} sr=${device.sampleRates?.joinToString()}"
    }
}
