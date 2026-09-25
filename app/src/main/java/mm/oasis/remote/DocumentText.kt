package mm.oasis.remote

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Достает текст из офисных документов: DOCX (word/document.xml) и ODT (content.xml).
 * Оба формата - zip с xml внутри, так что обходимся без сторонних библиотек.
 */
object DocumentText {
    const val DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val ODT_MIME = "application/vnd.oasis.opendocument.text"

    private const val MAX_CHARS = 2_000_000

    fun isSupported(mimeType: String): Boolean = mimeType == DOCX_MIME || mimeType == ODT_MIME

    fun extract(mimeType: String, bytes: ByteArray): String? = when (mimeType) {
        DOCX_MIME -> zipEntry(bytes, "word/document.xml")?.let(::docx)
        ODT_MIME -> zipEntry(bytes, "content.xml")?.let(::odt)
        else -> null
    }?.trim()

    private fun <T> zipEntry(bytes: ByteArray, name: String, parse: (InputStream) -> T): T? {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: return null
                if (entry.name == name) return parse(zip)
            }
        }
    }

    private fun zipEntry(bytes: ByteArray, name: String): XmlPullParser? =
        zipEntry(bytes, name) { stream ->
            val content = stream.readBytes()
            Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(ByteArrayInputStream(content), null)
            }
        }

    private fun docx(parser: XmlPullParser): String {
        val out = StringBuilder()
        var inText = false
        while (out.length < MAX_CHARS) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:t" -> inText = true
                    "w:tab" -> out.append('\t')
                    "w:br", "w:cr" -> out.append('\n')
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:t" -> inText = false
                    "w:p" -> out.append('\n')
                    "w:tc" -> out.append('\t')
                }
                XmlPullParser.TEXT -> if (inText) out.append(parser.text)
            }
        }
        return out.toString()
    }

    private fun odt(parser: XmlPullParser): String {
        val out = StringBuilder()
        var inBody = false
        while (out.length < MAX_CHARS) {
            when (parser.next()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.START_TAG -> when (parser.name) {
                    "office:body" -> inBody = true
                    "text:tab" -> out.append('\t')
                    "text:line-break" -> out.append('\n')
                    // text:s - несколько пробелов подряд, количество в text:c
                    "text:s" -> out.append(" ".repeat(parser.getAttributeValue(null, "text:c")?.toIntOrNull() ?: 1))
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "office:body" -> inBody = false
                    "text:p", "text:h" -> out.append('\n')
                    "table:table-cell" -> out.append('\t')
                }
                XmlPullParser.TEXT -> if (inBody) out.append(parser.text)
            }
        }
        return out.toString()
    }
}
