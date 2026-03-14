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

    private val encryptedFile = EncryptedFile.Builder(
        context,
        file,
        masterKey,
        EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
    ).build()

    var cache: MutableMap<String, String> = mutableMapOf()

    init {
        load()
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

    /*
    * load data to cache
    * */
    private fun load() {
        if (!file.exists()) return

        val text = encryptedFile.openFileInput().use {
            it.readBytes().decodeToString()
        }

        if (text.isBlank()) return

        cache = try {
            json.decodeFromString<Map<String, String>>(text).toMutableMap()
        } catch (e: Exception) {
            mutableMapOf()
        }
    }

    /*
    * load data to file
    * */
    fun flush() {
        if (file.exists()) {
            file.delete()
        }

        encryptedFile.openFileOutput().use {
            it.write(json.encodeToString(cache).toByteArray())
        }
    }
}