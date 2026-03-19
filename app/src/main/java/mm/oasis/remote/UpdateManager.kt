package mm.oasis.remote

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val browser_download_url: String,
    val name: String
)

class UpdateManager(private val context: Context) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    suspend fun checkForUpdates(currentVersion: String): GitHubRelease? {
        return try {
            val release: GitHubRelease = client.get("https://api.github.com/repos/MaxMerci/OASIS/releases/latest").body()
            if (isNewerVersion(currentVersion, release.tagName)) {
                release
            } else {
                null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun isNewerVersion(current: String, latest: String): Boolean {
        val currentClean = current.replace(Regex("[^0-9.]"), "")
        val latestClean = latest.replace(Regex("[^0-9.]"), "")
        
        val currentParts = currentClean.split(".").mapNotNull { it.toIntOrNull() }
        val latestParts = latestClean.split(".").mapNotNull { it.toIntOrNull() }
        
        val length = maxOf(currentParts.size, latestParts.size)
        for (i in 0 until length) {
            val currentVal = currentParts.getOrElse(i) { 0 }
            val latestVal = latestParts.getOrElse(i) { 0 }
            if (latestVal > currentVal) return true
            if (latestVal < currentVal) return false
        }
        return false
    }

    suspend fun downloadAndInstall(assetUrl: String) {
        withContext(Dispatchers.IO) {
            try {
                val response = client.get(assetUrl)
                val file = File(context.externalCacheDir, "update.apk")
                val channel: ByteReadChannel = response.bodyAsChannel()
                
                file.outputStream().use { output ->
                    channel.copyTo(output)
                }
                
                installApk(file)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun installApk(file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
