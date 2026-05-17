package com.thalock.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrHelper {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Upper bound on PDF pages we'll render + OCR for a single attachment.
     * Most ID / financial / insurance PDFs are 1–3 pages; capping here keeps
     * a malicious or accidental 500-page upload from blocking the UI thread
     * of the OCR coroutine for minutes.
     */
    private const val MAX_PDF_PAGES = 8

    /**
     * Render scale for PDF → Bitmap. ML Kit's Latin recognizer is sensitive
     * to low-DPI inputs; 2× the page's native point size is a reasonable
     * balance between recognition quality and memory pressure.
     */
    private const val PDF_RENDER_SCALE = 2

    suspend fun extractTextFromBitmap(bitmap: Bitmap): String =
        suspendCancellableCoroutine { cont ->
            val image = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }

    suspend fun extractTextFromUri(context: Context, uri: Uri): String {
        val image = InputImage.fromFilePath(context, uri)
        return suspendCancellableCoroutine { cont ->
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    cont.resume(result.text)
                }
                .addOnFailureListener { e ->
                    cont.resumeWithException(e)
                }
        }
    }

    /**
     * Render each page of the PDF behind [uri] to a bitmap and run ML Kit
     * text recognition on it, concatenating the page results. Works for both
     * scanned (image-only) and digitally-generated PDFs because we always
     * rasterize first — Android's [PdfRenderer] has no public API for pulling
     * the embedded text layer, so OCR is our single unified path.
     *
     * The URI must resolve to a seekable ParcelFileDescriptor; SAF-picked
     * `content://` URIs from OpenDocument satisfy this.
     */
    suspend fun extractTextFromPdfUri(context: Context, uri: Uri): String {
        val resolver = context.contentResolver
        val builder = StringBuilder()
        resolver.openFileDescriptor(uri, "r")?.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val pageCount = renderer.pageCount.coerceAtMost(MAX_PDF_PAGES)
                for (i in 0 until pageCount) {
                    renderer.openPage(i).use { page ->
                        val bitmap = Bitmap.createBitmap(
                            page.width * PDF_RENDER_SCALE,
                            page.height * PDF_RENDER_SCALE,
                            Bitmap.Config.ARGB_8888
                        )
                        // PdfRenderer draws onto a transparent canvas by
                        // default, which wrecks contrast for text recognition.
                        // Paint a white backdrop first so dark glyphs pop.
                        bitmap.eraseColor(Color.WHITE)
                        page.render(
                            bitmap,
                            null,
                            null,
                            PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        val pageText = runCatching { extractTextFromBitmap(bitmap) }
                            .getOrDefault("")
                        bitmap.recycle()
                        if (pageText.isNotBlank()) {
                            if (builder.isNotEmpty()) builder.append('\n')
                            builder.append(pageText)
                        }
                    }
                }
            }
        }
        return builder.toString()
    }

    fun parseExtractedFields(text: String): Map<String, String> {
        val fields = mutableMapOf<String, String>()
        val lines = text.lines().filter { it.isNotBlank() }

        for (line in lines) {
            // Try to detect key-value pairs with common separators
            val separators = listOf(":", "-", "=")
            for (sep in separators) {
                if (line.contains(sep)) {
                    val parts = line.split(sep, limit = 2)
                    if (parts.size == 2) {
                        val key = parts[0].trim().lowercase().replace(" ", "_")
                        val value = parts[1].trim()
                        if (key.isNotEmpty() && value.isNotEmpty()) {
                            fields[key] = value
                        }
                    }
                    break
                }
            }
        }

        // Also try to find common patterns
        val numberPattern = Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b")
        numberPattern.find(text)?.let { fields["detected_id_number"] = it.value }

        return fields
    }
}
