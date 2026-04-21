package com.thalock.app.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.thalock.app.data.dao.DocumentDao
import com.thalock.app.data.dao.UploadedFileDao
import com.thalock.app.data.model.Document
import com.thalock.app.data.model.UploadedFile
import com.thalock.app.security.AppLockManager
import com.thalock.app.security.SessionKey
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [Document::class, UploadedFile::class],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun documentDao(): DocumentDao
    abstract fun uploadedFileDao(): UploadedFileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Opens the SQLCipher-backed Room DB using the passphrase held in [SessionKey].
         *
         * Requires an unlocked session (PIN verified or biometric cipher authorized).
         * If [SessionKey] is empty but a legacy plaintext passphrase still exists in
         * prefs (pre-wrap installs), we fall back to it for this one open so the
         * unlock flow's migration path can promote it into a wrap before next launch.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildInstance(context).also { INSTANCE = it }
            }
        }

        private fun buildInstance(context: Context): AppDatabase {
            val appContext = context.applicationContext
            // NB: Room opens the DB lazily on first query, and SupportOpenHelperFactory
            // keeps a reference to this byte array for the DB's entire lifetime — so
            // we MUST NOT zero it after build(). SQLCipher's native layer owns the
            // long-lived copy; an extra JVM-heap copy here is unavoidable.
            val passphrase = SessionKey.get()
                ?: @Suppress("DEPRECATION")
                AppLockManager(appContext).getLegacyDatabasePassphrase()
                ?: error("Vault is locked — cannot open DB without a session key")

            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                "thalock_database"
            )
                .openHelperFactory(factory)
                // Real migrations go here as the schema evolves. We intentionally do
                // NOT call fallbackToDestructiveMigration(): a version bump must never
                // silently wipe the user's encrypted vault.
                // .addMigrations(MIGRATION_3_4, ...)
                .build()
        }

        /**
         * Close the DB and drop the cached instance. Call this on relock so the
         * next open must re-read [SessionKey] (which will be null after clear).
         */
        fun close() {
            synchronized(this) {
                INSTANCE?.close()
                INSTANCE = null
            }
        }
    }
}
