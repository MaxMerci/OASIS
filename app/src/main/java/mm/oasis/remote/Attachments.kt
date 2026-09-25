package mm.oasis.remote

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mm.oasis.serialization.dto.ContentPart
import mm.oasis.serialization.dto.FileData
import mm.oasis.serialization.dto.ImageUrl
import mm.oasis.serialization.dto.InputAudio
import mm.oasis.serialization.dto.VideoUrl
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.Locale

/**
 * Превращает файл пользователя в часть сообщения.
 * Медиа (картинки, аудио, видео, PDF) уходят в API как есть, в base64.
 * Остальное - только если это текст: имя + содержимое под заголовком <file ...>.
 * Из DOCX/ODT текст вытаскиваем сами, Google Docs сначала экспортируем через провайдер Drive.
 * Бинарники, из которых текст не достать, не прикрепляем вовсе.
 */
object Attachments {
    private const val MAX_TEXT_CHARS = 200_000
    private const val MAX_MEDIA_BYTES = 20L * 1024 * 1024
    private const val MAX_TEXT_BYTES = 10L * 1024 * 1024

    // в каком виде просить у Drive его документы (у них нет своего байтового содержимого), по убыванию
    private val EXPORT_TYPES = listOf("text/plain", "text/csv", DocumentText.DOCX_MIME, DocumentText.ODT_MIME, "application/pdf")

    private enum class Kind { IMAGE, AUDIO, VIDEO, PDF, TEXT }

    suspend fun read(resolver: ContentResolver, uri: Uri): ContentPart = withContext(Dispatchers.IO) {
        val (queriedName, queriedSize) = query(resolver, uri)
        val fileName = queriedName ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        val originalMime = resolveMime(resolver, uri, fileName)
        val exportMime = if (originalMime.startsWith("application/vnd.google-apps.")) {
            resolver.getStreamTypes(uri, "*/*")?.let { types -> EXPORT_TYPES.firstOrNull { it in types } }
                ?: throw IllegalStateException("CAN'T EXPORT $fileName")
        } else null
        val mimeType = exportMime ?: originalMime
        val kind = when {
            mimeType.startsWith("image/") -> Kind.IMAGE
            mimeType.startsWith("audio/") -> Kind.AUDIO
            mimeType.startsWith("video/") -> Kind.VIDEO
            mimeType == "application/pdf" -> Kind.PDF
            else -> Kind.TEXT
        }

        // лимит проверяем до чтения, чтобы не тащить в память гигабайтное видео
        val limit = if (kind == Kind.TEXT) MAX_TEXT_BYTES else MAX_MEDIA_BYTES
        // у документов Drive размер 0 или неизвестен, настоящий узнаем только при чтении
        if (exportMime == null && queriedSize != null && queriedSize > limit) {
            throw IllegalStateException("$fileName IS TOO LARGE (${formatSize(queriedSize)} > ${formatSize(limit)})")
        }
        val stream = if (exportMime != null) {
            resolver.openTypedAssetFileDescriptor(uri, exportMime, null)?.createInputStream()
        } else {
            resolver.openInputStream(uri)
        } ?: throw IllegalStateException("CAN'T READ $fileName")
        val bytes = readLimited(stream, fileName, limit)
        val size = queriedSize?.takeIf { exportMime == null } ?: bytes.size.toLong()

        when (kind) {
            Kind.IMAGE -> ContentPart.ImagePart(
                imageUrl = ImageUrl(dataUrl(mimeType, bytes)),
                fileName = fileName
            )
            Kind.AUDIO -> ContentPart.AudioPart(
                inputAudio = InputAudio(base64(bytes), audioFormat(mimeType, fileName)),
                fileName = fileName
            )
            Kind.VIDEO -> ContentPart.VideoPart(
                videoUrl = VideoUrl(dataUrl(mimeType, bytes)),
                fileName = fileName
            )
            Kind.PDF -> ContentPart.FilePart(
                file = FileData(filename = fileName, fileData = dataUrl(mimeType, bytes)),
                fileName = fileName
            )
            Kind.TEXT -> {
                val text = (if (DocumentText.isSupported(mimeType)) extractDocument(mimeType, bytes) else decodeText(bytes))
                    ?: throw IllegalStateException("CAN'T EXTRACT TEXT FROM $fileName")
                ContentPart.TextPart(text = asText(fileName, mimeType, size, text), fileName = fileName)
            }
        }
    }

    // размер от провайдера бывает неизвестен или врет, поэтому режем и при чтении
    // битый документ - то же, что нечитаемый бинарник
    private fun extractDocument(mimeType: String, bytes: ByteArray): String? = try {
        DocumentText.extract(mimeType, bytes)
    } catch (e: Exception) {
        null
    }

    private fun readLimited(input: InputStream, fileName: String, limit: Long): ByteArray {
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > limit) throw IllegalStateException("$fileName IS TOO LARGE (> ${formatSize(limit)})")
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun base64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun dataUrl(mimeType: String, bytes: ByteArray): String = "data:$mimeType;base64,${base64(bytes)}"

    private fun asText(fileName: String, mimeType: String, size: Long, text: String): String {
        val attrs = mutableListOf(
            "name" to fileName,
            "type" to mimeType,
            "size" to formatSize(size),
            "lines" to text.lines().size.toString()
        )
        val body = if (text.length > MAX_TEXT_CHARS) {
            attrs += "truncated" to "first $MAX_TEXT_CHARS of ${text.length} chars"
            text.take(MAX_TEXT_CHARS)
        } else {
            text
        }

        val header = attrs.joinToString(" ") { (k, v) -> "$k=\"${v.replace("\"", "'")}\"" }
        return "<file $header>\n$body\n</file>"
    }

    // UTF-16 узнаем по BOM, иначе строгий UTF-8; плюс мало управляющих символов = текст, иначе бинарник
    private fun decodeText(bytes: ByteArray): String? {
        val charset = when {
            bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
            bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
            else -> Charsets.UTF_8
        }
        val decoder = charset.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        val text = try {
            decoder.decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: CharacterCodingException) {
            return null
        }.removePrefix("\uFEFF")

        val sample = text.take(8192)
        val control = sample.count { it < ' ' && it != '\n' && it != '\r' && it != '\t' }
        return if (sample.isNotEmpty() && control * 100 / sample.length > 1) null else text
    }

    private fun query(resolver: ContentResolver, uri: Uri): Pair<String?, Long?> {
        resolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                val name = if (nameIndex != -1) cursor.getString(nameIndex) else null
                val size = if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
                return name to size
            }
        }
        return null to null
    }

    // провайдеры часто отдают octet-stream даже для .md или .kt, тогда смотрим на расширение
    private fun resolveMime(resolver: ContentResolver, uri: Uri, fileName: String): String {
        val fromResolver = resolver.getType(uri)
        if (fromResolver != null && fromResolver != "application/octet-stream") return fromResolver
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: when (ext) {
                "docx" -> DocumentText.DOCX_MIME
                "odt" -> DocumentText.ODT_MIME
                else -> null
            }
            ?: fromResolver
            ?: "application/octet-stream"
    }

    // API ждет формат файла ("mp3", "wav"), а не подтип mime ("mpeg", "x-wav")
    private fun audioFormat(mimeType: String, fileName: String): String =
        when (val subtype = mimeType.substringAfter('/').lowercase(Locale.ROOT)) {
            "mpeg", "mp3", "mpeg3", "x-mpeg-3" -> "mp3"
            "wav", "x-wav", "wave", "vnd.wave" -> "wav"
            "mp4", "x-m4a", "m4a", "aac" -> "m4a"
            else -> fileName.substringAfterLast('.', subtype).lowercase(Locale.ROOT)
        }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
