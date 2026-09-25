package mm.oasis.ui.chat

import android.content.Intent
import android.net.Uri
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * Processing the “Shared” interface on Android
 */
object SharedInbox {
    val files = MutableStateFlow<List<Uri>>(emptyList())
    val text = MutableStateFlow<String?>(null)

    fun accept(intent: Intent?): Boolean {
        val uris = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            )
            Intent.ACTION_SEND_MULTIPLE ->
                IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> return false
        }.ifEmpty {
            intent.clipData?.let { clip -> (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri } }.orEmpty()
        }

        if (uris.isNotEmpty()) {
            files.update { it + uris }
            return true
        }

        val shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: return false
        text.update { if (it.isNullOrEmpty()) shared else "$it\n$shared" }
        return true
    }
}
