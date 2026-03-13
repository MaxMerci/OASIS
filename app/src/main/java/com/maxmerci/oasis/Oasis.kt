package com.maxmerci.oasis

import android.content.Context

object Oasis {
    lateinit var applicationContext: Context
        private set

    fun init(context: Context) {
        applicationContext = context.applicationContext
    }
}