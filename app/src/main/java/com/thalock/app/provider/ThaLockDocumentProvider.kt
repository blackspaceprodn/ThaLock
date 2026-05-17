package com.thalock.app.provider

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import com.thalock.app.R
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.UploadedFile
import com.thalock.app.security.SafExposurePreference
import com.thalock.app.security.SessionKey
import com.thalock.app.util.DocumentFileGenerator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * A DocumentsProvider that exposes ThaLock's *uploaded files* (the actual
 * documents the user has attached — PDFs, photos, scans, etc.) to Android's
 * Storage Access Framework. When any app triggers a file picker, ThaLock
 * appears as a source and serves real decrypted file content, not a text
 * summary of a saved ID record.
 *
 * Saved Documents (IDs, cards, insurance metadata) are NOT exposed through
 * this provider — they are in-app structured data, not shareable files. If
 * the user wants to share the raw photo/PDF they scanned, that lives as an
 * UploadedFile and is surfaced here.
 *
 * Session-gated: file listings / reads only work when [SessionKey] is
 * unlocked AND the user hasn't disabled SAF exposure in Settings.
 */
class ThaLockDocumentProvider : DocumentsProvider() {

    companion object {
        private const val ROOT_ID = "thalock_vault"
        private const val ROOT_DOC_ID = "root"

        private val ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_MIME_TYPES,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_ICON,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID
        )

        private val DOC_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_SIZE
        )
    }

    private fun getDatabase(): AppDatabase {
        return AppDatabase.getInstance(context!!)
    }

    /** Background handler for delete-on-close callbacks from [openDocument]. */
    private val closeHandlerThread by lazy {
        HandlerThread("ThaLockPfdClose").also { it.start() }
    }
    private val closeHandler by lazy { Handler(closeHandlerThread.looper) }

    /**
     * Two gates must be open before we serve any documents:
     *   1. The vault is unlocked (a recent PIN/biometric auth).
     *   2. The user has not disabled SAF exposure from Settings.
     *
     * Either gate being closed means empty results / SecurityException.
     */
    private fun canServe(): Boolean {
        val ctx = context ?: return false
        return SessionKey.isUnlocked() && SafExposurePreference.isEnabled(ctx)
    }

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: ROOT_PROJECTION)
        result.newRow().apply {
            add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT_ID)
            // Advertise a broad mime set so pickers from any app surface us.
            // The actual per-file MIME is reported from queryDocument/queryChildDocuments.
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
            add(
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.FLAG_LOCAL_ONLY
            )
            add(DocumentsContract.Root.COLUMN_ICON, R.mipmap.ic_launcher)
            add(DocumentsContract.Root.COLUMN_TITLE, "ThaLock Vault")
            add(DocumentsContract.Root.COLUMN_SUMMARY, "Your secure documents")
            add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
        }
        return result
    }

    override fun queryDocument(documentId: String?, projection: Array<out String>?): Cursor {
        val result = MatrixCursor(projection ?: DOC_PROJECTION)

        if (documentId == ROOT_DOC_ID) {
            result.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, ROOT_DOC_ID)
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.MIME_TYPE_DIR)
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "ThaLock Vault")
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, System.currentTimeMillis())
                add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                add(DocumentsContract.Document.COLUMN_SIZE, 0L)
            }
        } else if (canServe()) {
            val id = documentId?.toLongOrNull() ?: return result
            val file = runBlocking {
                getDatabase().uploadedFileDao().getFileById(id)
            } ?: return result
            addUploadedFileRow(result, file)
        }

        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DOC_PROJECTION)

        if (!canServe()) return result

        if (parentDocumentId == ROOT_DOC_ID) {
            // Snapshot the Flow on the current (binder) thread via runBlocking +
            // first(). The provider is called synchronously by SAF, so we must
            // not suspend back to a UI dispatcher here.
            val files = runBlocking {
                getDatabase().uploadedFileDao().getAllFiles().first()
            }
            for (file in files) {
                addUploadedFileRow(result, file)
            }
        }

        return result
    }

    override fun openDocument(
        documentId: String?,
        mode: String?,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        if (!canServe()) {
            throw SecurityException("Vault is locked or SAF exposure is disabled")
        }

        val id = documentId?.toLongOrNull()
            ?: throw IllegalArgumentException("Invalid document ID")

        val uploaded = runBlocking {
            getDatabase().uploadedFileDao().getFileById(id)
        } ?: throw IllegalStateException("File not found")

        // Materialize the encrypted blob into a plaintext cache file for the
        // duration of the consumer's read, then delete it the moment they
        // close the descriptor. Never leave decrypted bytes on disk longer
        // than the share lasts.
        val plaintext = DocumentFileGenerator.materializeUploadedFile(context!!, uploaded)

        return ParcelFileDescriptor.open(
            plaintext,
            ParcelFileDescriptor.MODE_READ_ONLY,
            closeHandler
        ) { plaintext.delete() }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private fun addUploadedFileRow(cursor: MatrixCursor, file: UploadedFile) {
        val ext = DocumentFileGenerator.extensionFor(file)
        val displayName = if (!file.name.contains('.') && !ext.isNullOrBlank()) {
            "${file.name}.$ext"
        } else {
            file.name
        }
        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, file.id.toString())
            add(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                file.mimeType ?: "application/octet-stream"
            )
            add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, file.createdAt)
            add(DocumentsContract.Document.COLUMN_FLAGS, 0)
            add(DocumentsContract.Document.COLUMN_SIZE, file.sizeBytes)
        }
    }
}
