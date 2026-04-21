package com.thalock.app.util

import android.graphics.Bitmap
import android.net.Uri
import android.content.Context
import android.provider.MediaStore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OcrHelper {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

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
