package com.kimbaeksoo.pdftoepub

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class PdfToEpubApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(applicationContext)
    }
}
