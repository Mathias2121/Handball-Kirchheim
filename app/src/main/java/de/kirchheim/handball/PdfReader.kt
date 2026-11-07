package de.kirchheim.handball

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.InputStream

/** Extrahiert den Text eines PDF-Spielberichts mit PDFBox (läuft lokal auf dem Handy). */
object PdfReader {
    private var initialized = false

    @Synchronized
    private fun init(context: Context) {
        if (!initialized) {
            PDFBoxResourceLoader.init(context.applicationContext)
            initialized = true
        }
    }

    fun textLines(context: Context, input: InputStream): List<String> {
        init(context)
        val doc = PDDocument.load(input)
        try {
            val stripper = PDFTextStripper()
            stripper.sortByPosition = false
            return stripper.getText(doc).lines()
        } finally {
            doc.close()
        }
    }
}
