package mm.oasis.remote

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import mm.oasis.Oasis
import java.io.File

class Storage(name: String) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val dir = File(Oasis.applicationContext.filesDir, name).apply { mkdirs() }
    private val legacy = File(dir.parentFile, "$name.secure")
    private val hashes = mutableMapOf<String, Int>()

    fun keys(): Set<String> = dir.list()?.toSet().orEmpty()

    fun <T> get(key: String, serializer: KSerializer<T>): T? = try {
        val text = read(File(dir, key))
        json.decodeFromString(serializer, text).also { hashes[key] = text.hashCode() }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }

    fun <T> put(key: String, value: T, serializer: KSerializer<T>) {
        val text = json.encodeToString(serializer, value)
        if (hashes[key] == text.hashCode()) return
        val tmp = File(dir, "$key.tmp")
        aead.newEncryptingStream(tmp.outputStream(), key.toByteArray()).use { it.write(text.toByteArray()) }
        tmp.renameTo(File(dir, key))
        hashes[key] = text.hashCode()
    }

    fun remove(key: String) {
        File(dir, key).delete()
        hashes.remove(key)
    }

    fun migrate(block: (Map<String, String>) -> Unit) {
        if (!legacy.exists()) return
        block(json.decodeFromString(read(legacy)))
        legacy.delete()
    }

    private fun read(file: File) = aead.newDecryptingStream(file.inputStream(), file.name.toByteArray())
        .use { it.readBytes().decodeToString() }

    private companion object {
        val aead: StreamingAead by lazy {
            StreamingAeadConfig.register()
            AndroidKeysetManager.Builder()
                .withSharedPref(
                    Oasis.applicationContext,
                    "__androidx_security_crypto_encrypted_file_keyset__",
                    "__androidx_security_crypto_encrypted_file_pref__"
                )
                .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
                .withMasterKeyUri("android-keystore://_androidx_security_master_key_")
                .build()
                .keysetHandle
                .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
        }
    }
}
