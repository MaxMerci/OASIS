package mm.oasis.remote

import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import mm.oasis.Oasis
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

class Storage(
    name: String,
    val json: Json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
) {
    private val context = Oasis.applicationContext

    private val file = File(context.filesDir, "$name.secure")

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    var cache: MutableMap<String, String> = mutableMapOf()

    init {
        load()
    }

    private fun getEncryptedFile(targetFile: File): EncryptedFile {
        return EncryptedFile.Builder(
            context,
            targetFile,
            masterKey,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
    }

    inline fun <reified T> put(key: String, value: T) {
        cache[key] = json.encodeToString(
            json.serializersModule.serializer(),
            value
        )
    }

    fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
        cache[key] = json.encodeToString(serializer, value)
    }

    inline fun <reified T> get(key: String): T? {
        val raw = cache[key] ?: return null
        val serializer = json.serializersModule.serializer<T>()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            null
        }
    }

    fun <T> get(key: String, serializer: KSerializer<T>): T? {
        val raw = cache[key] ?: return null
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            null
        }
    }

    private fun load() {
        if (!file.exists()) return

        try {
            val text = getEncryptedFile(file).openFileInput().use {
                it.readBytes().decodeToString()
            }

            if (text.isBlank()) return

            cache = json.decodeFromString<Map<String, String>>(text).toMutableMap()
        } catch (e: Exception) {
            e.printStackTrace()
            cache = mutableMapOf()
        }
    }

    fun flush() {
        try {
            // Я уже попался на это один раз
            // EncryptedFile требует, чтобы файл НЕ существовал перед записью
            if (file.exists()) {
                file.delete()
            }

            getEncryptedFile(file).openFileOutput().use {
                it.write(json.encodeToString(cache).toByteArray())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}