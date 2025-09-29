package com.example.oiarlete

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.oiarlete.diagnostics.DiagnosticsBus

class MediaControlService : NotificationListenerService() {

    private val mediaSessionManager: MediaSessionManager?
        get() = getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaControlService: inicializado"))
    }

    override fun onDestroy() {
        if (activeInstance === this) {
            activeInstance = null
        }
        DiagnosticsBus.emit(DiagnosticsBus.Event.Info("MediaControlService: finalizado"))
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // no-op: keep service alive by acknowledging notifications
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    private fun playFromSearchInternal(
        packageNames: List<String>,
        query: String,
        serviceName: String,
        extrasBuilder: () -> Bundle
    ): Boolean {
        val manager = mediaSessionManager ?: return false
        val component = ComponentName(this, javaClass)
        val controllers = try {
            manager.getActiveSessions(component) ?: emptyList()
        } catch (e: SecurityException) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: acesso a MediaSession negado - ${e.message}"))
            return false
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: falha ao obter sessões de mídia - ${e.message}"))
            return false
        }

        if (controllers.isEmpty()) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: nenhuma sessão de mídia ativa encontrada"))
            return false
        }

        var executed = false
        controllers.forEach { controller ->
            if (packageNames.contains(controller.packageName)) {
                try {
                    val extras = extrasBuilder()
                    controller.transportControls.playFromSearch(query, extras)
                    DiagnosticsBus.emit(
                        DiagnosticsBus.Event.Info(
                            "$serviceName: playFromSearch via NotificationListener para ${controller.packageName}"
                        )
                    )
                    executed = true
                    return@forEach
                } catch (e: Exception) {
                    DiagnosticsBus.emit(
                        DiagnosticsBus.Event.Info(
                            "$serviceName: erro playFromSearch via NotificationListener - ${e.message}"
                        )
                    )
                }
            }
        }

        if (!executed) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: nenhuma sessão correspondeu aos pacotes $packageNames"))
        }

        return executed
    }

    private fun controlActiveSessions(action: String, control: (MediaController) -> Unit): Boolean {
        val manager = mediaSessionManager ?: return false
        val component = ComponentName(this, javaClass)
        val controllers = try {
            manager.getActiveSessions(component) ?: emptyList()
        } catch (e: SecurityException) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: acesso negado para $action - ${e.message}"))
            return false
        } catch (e: Exception) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: falha ao obter sessões para $action - ${e.message}"))
            return false
        }

        if (controllers.isEmpty()) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: nenhuma sessão ativa para $action"))
            return false
        }

        var executed = false
        controllers.forEach { controller ->
            try {
                control(controller)
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: $action em ${controller.packageName}"))
                executed = true
            } catch (e: Exception) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: erro ao executar $action em ${controller.packageName} - ${e.message}"))
            }
        }

        if (!executed) {
            DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: nenhuma sessão aceitou $action"))
        }

        return executed
    }

    companion object {
        @Volatile
        private var activeInstance: MediaControlService? = null

        fun playFromSearch(
            packageNames: List<String>,
            query: String,
            serviceName: String,
            extrasBuilder: () -> Bundle
        ): Boolean {
            val instance = activeInstance
            if (instance == null) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("$serviceName: MediaControlService não está ativo ou sem permissão"))
                return false
            }
            return instance.playFromSearchInternal(packageNames, query, serviceName, extrasBuilder)
        }

        fun pauseActiveSessions(): Boolean {
            val instance = activeInstance
            if (instance == null) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: serviço não ativo para pausa"))
                return false
            }
            return instance.controlActiveSessions("pausa") { controller ->
                controller.transportControls.pause()
            }
        }

        fun stopActiveSessions(): Boolean {
            val instance = activeInstance
            if (instance == null) {
                DiagnosticsBus.emit(DiagnosticsBus.Event.Info("Controle de mídia: serviço não ativo para stop"))
                return false
            }
            return instance.controlActiveSessions("stop") { controller ->
                controller.transportControls.stop()
            }
        }
    }
}
