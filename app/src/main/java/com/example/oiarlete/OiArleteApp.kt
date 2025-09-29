package com.example.oiarlete

import android.app.Application

class OiArleteApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGlobals.init(this)
    }
}
