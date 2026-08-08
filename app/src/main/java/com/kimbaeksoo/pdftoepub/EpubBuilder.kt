package com.kimbaeksoo.pdftoepub

import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Minimal, dependency-free EPUB 3 writer. Builds an EPUB with an OPF package document,
 * an EPUB3 nav document, and an NCX fallback for older reading apps, from a list of
 * plain-text chapters.
 */
class EpubBuilder(private val title: String, private val chapters: List<PdfChapter>) {

    private val bookId: String = "urn:uuid:${UUID.randomUUID()}"

    fun writeTo(out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            writeStoredEntry(zip, "mimetype", "application/epub+zip")

            writeEntry(zip, "META-INF/container.xml", containerXml())
            writeEntry(zip, "OEBPS/content.opf", contentOpf())
            writeEntry(zip, "OEBPS/nav.xhtml", navXhtml())
            writeEntry(zip, "OEBPS/toc.ncx", tocNcx())

            chapters.forEachIndexed { index, chapter ->
                writeEntry(zip, "OEBPS/text/chapter${index + 1}.xhtml", chapterXhtml(chapter))
            }
        }
    }

    /** The "mimetype" entry must be the first entry and stored uncompressed per the EPUB spec. */
    private fun writeStoredEntry(zip: ZipOutputStream, name: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.US_ASCII)
        val entry = ZipEntry(name).apply {
            method = ZipEntry.STORED
            size = bytes.size.toLong()
            compressedSize = bytes.size.toLong()
            crc = CRC32().apply { update(bytes) }.value
        }
        zip.putNextEntry(entry)
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private fun containerXml() = """
        <?xml version="1.0" encoding="UTF-8"?>
        <container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
          <rootfiles>
            <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
          </rootfiles>
        </container>
    """.trimIndent()

    private fun contentOpf(): String {
        val manifestItems = chapters.indices.joinToString("\n") { i ->
            """    <item id="chapter${i + 1}" href="text/chapter${i + 1}.xhtml" media-type="application/xhtml+xml"/>"""
        }
        val spineItems = chapters.indices.joinToString("\n") { i ->
            """    <itemref idref="chapter${i + 1}"/>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="BookId">
              <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
                <dc:identifier id="BookId">$bookId</dc:identifier>
                <dc:title>${escapeXml(title)}</dc:title>
                <dc:language>ko</dc:language>
                <meta property="dcterms:modified">${isoTimestamp()}</meta>
              </metadata>
              <manifest>
                <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
                <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
${manifestItems}
              </manifest>
              <spine toc="ncx">
${spineItems}
              </spine>
            </package>
        """.trimIndent()
    }

    private fun navXhtml(): String {
        val links = chapters.indices.joinToString("\n") { i ->
            """      <li><a href="text/chapter${i + 1}.xhtml">${escapeXml(chapters[i].title)}</a></li>"""
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops">
            <head><title>${escapeXml(title)}</title></head>
            <body>
              <nav epub:type="toc" id="toc">
                <h1>${escapeXml(title)}</h1>
                <ol>
${links}
                </ol>
              </nav>
            </body>
            </html>
        """.trimIndent()
    }

    private fun tocNcx(): String {
        val navPoints = chapters.indices.joinToString("\n") { i ->
            """
            <navPoint id="navPoint-${i + 1}" playOrder="${i + 1}">
              <navLabel><text>${escapeXml(chapters[i].title)}</text></navLabel>
              <content src="text/chapter${i + 1}.xhtml"/>
            </navPoint>
            """.trimIndent()
        }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
              <head>
                <meta name="dtb:uid" content="$bookId"/>
              </head>
              <docTitle><text>${escapeXml(title)}</text></docTitle>
              <navMap>
${navPoints}
              </navMap>
            </ncx>
        """.trimIndent()
    }

    private fun chapterXhtml(chapter: PdfChapter): String {
        val paragraphs = chapter.text
            .split(Regex("\\r?\\n\\s*\\r?\\n|\\r?\\n"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n") { "    <p>${escapeXml(it)}</p>" }
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <html xmlns="http://www.w3.org/1999/xhtml">
            <head><title>${escapeXml(chapter.title)}</title></head>
            <body>
              <h2>${escapeXml(chapter.title)}</h2>
${paragraphs}
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun isoTimestamp(): String {
        val now = java.util.Date()
        val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
        format.timeZone = java.util.TimeZone.getTimeZone("UTC")
        return format.format(now)
    }
}
