package com.kimbaeksoo.pdftoepub

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

/** One chapter worth of extracted PDF pages, ready to become an EPUB xhtml file. */
data class PdfChapter(val title: String, val text: String)

class PdfTextExtractor(private val context: Context) {

    /**
     * Extracts text from [uri] page by page and groups every [pagesPerChapter] pages into
     * one [PdfChapter] so the resulting EPUB has a manageable table of contents instead of
     * one entry per page.
     */
    fun extract(
        uri: Uri,
        pagesPerChapter: Int = 15,
        onProgress: (page: Int, totalPages: Int) -> Unit
    ): List<PdfChapter> {
        val resolver = context.contentResolver
        val document = resolver.openInputStream(uri)?.use { PDDocument.load(it) }
            ?: throw IllegalStateException("PDF 파일을 열 수 없습니다.")

        document.use { doc ->
            val totalPages = doc.numberOfPages
            if (totalPages == 0) return emptyList()

            val stripper = PDFTextStripper()
            val chapters = mutableListOf<PdfChapter>()
            var chapterStart = 1

            while (chapterStart <= totalPages) {
                val chapterEnd = minOf(chapterStart + pagesPerChapter - 1, totalPages)
                stripper.startPage = chapterStart
                stripper.endPage = chapterEnd
                val text = stripper.getText(doc)
                chapters.add(PdfChapter(title = "Pages $chapterStart-$chapterEnd", text = text))
                onProgress(chapterEnd, totalPages)
                chapterStart = chapterEnd + 1
            }

            return chapters
        }
    }
}
