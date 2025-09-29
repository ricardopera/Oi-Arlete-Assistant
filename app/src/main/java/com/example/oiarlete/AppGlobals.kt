package com.example.oiarlete

import android.app.Application
import android.content.Context

object AppGlobals {
    private var appCtx: Context? = null
    fun init(app: Application) { appCtx = app.applicationContext }
    fun get(): Context {
        return appCtx ?: run {
            // Fallback para refletir Application inicial
            val appClass = Class.forName("android.app.AppGlobals")
            val method = appClass.getDeclaredMethod("getInitialApplication")
            method.isAccessible = true
            method.invoke(null) as Context
        }
    }
}
