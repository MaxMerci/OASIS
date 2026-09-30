package mm.oasis.ui.objects

import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.google.android.material.snackbar.Snackbar
import mm.oasis.remote.Workspace
import java.io.File
import java.util.Locale


object WorkspaceFiles {
    fun open(view: View, path: String) {
        val file = Workspace.resolve(path)
        if (file == null) {
            Snackbar.make(view, "FILE ${path.substringAfterLast('/')} NO LONGER EXISTS", Snackbar.LENGTH_SHORT).show()
            return
        }
        open(view, file)
    }

    fun open(view: View, file: File) {
        val context = view.context
        val name = file.name
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val ext = file.extension.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, name))
        } catch (e: ActivityNotFoundException) {
            Snackbar.make(view, "NO APP TO OPEN $name", Snackbar.LENGTH_SHORT).show()
        }
    }
}
