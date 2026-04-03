package mm.oasis

import android.content.Context
import mm.oasis.remote.registerTools

object Oasis {
    lateinit var applicationContext: Context
        private set

    fun init(context: Context) {
        applicationContext = context.applicationContext
        registerTools()
    }
}