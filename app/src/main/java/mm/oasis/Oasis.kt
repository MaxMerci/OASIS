package mm.oasis

import android.content.Context
import mm.oasis.remote.registerTools
import mm.oasis.remote.tools.WebSearch
import java.io.File

object Oasis {
    lateinit var applicationContext: Context
        private set
    lateinit var filesDir: File

    fun init(context: Context) {
        applicationContext = context.applicationContext
        filesDir = applicationContext.filesDir
        registerTools(listOf(
            WebSearch
        ))
    }
}