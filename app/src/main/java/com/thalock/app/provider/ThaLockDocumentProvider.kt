package com.thalock.app.provider

import android.content.res.AssetFileDescriptor
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
import com.thalock.app.security.SafExposurePreference
import com.thalock.app.security.SessionKey
import com.thalock.app.util.DocumentFileGenerator
import java.io.File

/**
 * A DocumentsProvider that exposes ThaLock vault documents to Android's
 * Storage Access Framework (SAF). When any app triggers a file picker
 * (e.g. "Attach document"), ThaLock appears as a source.
 *
 * Session-gated: documents are only served when [SessionKey] is unlocked
 * (i.e. the user has authenticated in ThaLock recently). If locked,
 * queries return empty results so no data leaks.
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

    /**
     * Background thread that receives the close callback for each exported
     * plaintext file. Required because [ParcelFileDescriptor.open] with a
     * close listener needs a Handler, and we must not block the main thread
     * waiting to delete the file on close.
     */
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
            add(DocumentsContract.Root.COLUMN_MIME_TYPES, "text/plain")
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
            val doc = getDatabase().documentDao().getDocumentByIdSync(id) ?: return result
            val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")

            result.newRow().apply {
                add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, doc.id.toString())
                add(DocumentsContract.Document.COLUMN_MIME_TYPE, "text/plain")
                add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, "${safeTitle}.txt")
                add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, doc.updatedAt)
                add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                add(DocumentsContract.Document.COLUMN_SIZE, null)
            }
        }

        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String?,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val result = MatrixCursor(projection ?: DOC_PROJECTION)

        // Only serve documents when the vault is unlocked AND the user hasn't
        // disabled SAF exposure from Settings.
        if (!canServe()) return result

        if (parentDocumentId == ROOT_DOC_ID) {
            val documents = getDatabase().documentDao().getAllDocumentsSync()
            for (doc in documents) {
                val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val subtitle = doc.country
                    ?.let { "${it.displayName} - ${doc.documentType.displayName}" }
                    ?: doc.documentType.displayName

                result.newRow().apply {
                    add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, doc.id.toString())
                    add(DocumentsContract.Document.COLUMN_MIME_TYPE, "text/plain")
                    add(
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        "${doc.title} ($subtitle).txt"
                    )
                    add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, doc.updatedAt)
                    add(DocumentsContract.Document.COLUMN_FLAGS, 0)
                    add(DocumentsContract.Document.COLUMN_SIZE, null)
                }
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

        val doc = getDatabase().documentDao().getDocumentByIdSync(id)
            ?: throw IllegalStateException("Document not found")

        val file = DocumentFileGenerator.generateTextFile(context!!, doc)

        // Delete the plaintext export as soon as the consuming app closes its
        // file descriptor. This keeps the cache from accumulating decrypted
        // documents after a share / attach completes.
        return ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_ONLY,
            closeHandler
        ) { file.delete() }
    }
}
