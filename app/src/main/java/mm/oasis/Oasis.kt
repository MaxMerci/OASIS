package mm.oasis

import android.content.Context
import mm.oasis.remote.registerTools
import mm.oasis.remote.tools.DuckDuckGoAPI
import mm.oasis.remote.tools.WebSearch

object Oasis {
    lateinit var applicationContext: Context
        private set

    fun init(context: Context) {
        applicationContext = context.applicationContext
        registerTools(listOf(
            WebSearch,
            DuckDuckGoAPI
        ))
    }
}