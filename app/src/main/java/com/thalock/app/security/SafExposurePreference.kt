package com.thalock.app.security

import android.content.Context
import android.content.SharedPreferences

/**
 * User-controlled gate for whether [com.thalock.app.provider.ThaLockDocumentProvider]
 * serves documents to the Storage Access Framework.
 *
 * When off, the provider returns empty/locked results even while the vault is
 * unlocked — useful for users who want to open the app locally without
 * exposing documents to system pickers or any process holding
 * `MANAGE_DOCUMENTS`.
 *
 * Stored in plain [SharedPreferences] (the flag itself is not sensitive) so
 * the provider can read it without bootstrapping the encrypted prefs.
 */
object SafExposurePreference {
    private const val PREFS = "thalock_saf_prefs"
    private const val KEY_ENABLED = "saf_exposure_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }
}
