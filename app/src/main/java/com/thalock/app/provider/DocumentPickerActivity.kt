package com.thalock.app.provider

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import com.thalock.app.data.database.AppDatabase
import com.thalock.app.data.model.Document
import com.thalock.app.security.AppLockManager
import com.thalock.app.security.BiometricHelper
import com.thalock.app.security.LockMethod
import com.thalock.app.security.SessionKey
import com.thalock.app.ui.theme.ThaLockTheme
import com.thalock.app.util.DocumentFileGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Activity shown when an external app requests to pick/attach a document.
 * Authenticates the user first, then shows the document list to select from.
 */
class DocumentPickerActivity : FragmentActivity() {

    private lateinit var lockManager: AppLockManager
    private lateinit var biometricHelper: BiometricHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        // Reject direct launches from other apps that bypass the intent-filter
        // (e.g. explicit-intent probes). Only the documented PICK / GET_CONTENT
        // actions are honored.
        val action = intent?.action
        if (action != Intent.ACTION_PICK && action != Intent.ACTION_GET_CONTENT) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        lockManager = AppLockManager(this)
        biometricHelper = BiometricHelper(this)

        setContent {
            ThaLockTheme {
                var isAuthenticated by remember { mutableStateOf(false) }
                var authError by remember { mutableStateOf<String?>(null) }

                if (!isAuthenticated) {
                    val triggerBiometric = {
                        unlockWithBiometric(
                            onUnlocked = { isAuthenticated = true; authError = null },
                            onFailed = { authError = it }
                        )
                    }
                    PickerAuthScreen(
                        lockMethod = lockManager.lockMethod,
                        error = authError,
                        onBiometricAuth = triggerBiometric,
                        onPinSubmit = { pin ->
                            if (lockManager.isLockedOut) {
                                authError = "Too many attempts. Please wait."
                            } else if (lockManager.verifyPin(pin)) {
                                isAuthenticated = true
                                authError = null
                            } else {
                                authError = if (lockManager.isLockedOut) {
                                    "Too many attempts. Locked for 1 minute."
                                } else {
                                    "Incorrect PIN (${AppLockManager.MAX_ATTEMPTS - lockManager.failedAttempts} tries left)"
                                }
                            }
                        },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )

                    LaunchedEffect(Unit) {
                        if (lockManager.lockMethod == LockMethod.BIOMETRIC) {
                            triggerBiometric()
                        }
                    }
                } else {
                    DocumentSelectionScreen(
                        onDocumentSelected = { document -> shareDocument(document) },
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }

    /**
     * Biometric unlock for the picker. Mirrors the logic in [com.thalock.app.MainActivity]
     * so vaulted documents are only exposed after the user holds a valid auth'd Cipher
     * against the Keystore wrap — no "identity-only" biometric here.
     */
    private fun unlockWithBiometric(
        onUnlocked: () -> Unit,
        onFailed: (String) -> Unit
    ) {
        // Without a bio-wrap there is no DB passphrase to unwrap; the user must onboard
        // biometric in the main app first.
        if (!lockManager.hasBioWrap) {
            onFailed("Open ThaLock to set up biometric first")
            return
        }
        val cipher = try {
            lockManager.buildBioDecryptCipher()
        } catch (t: Throwable) {
            if (lockManager.isBioKeyInvalidated(t)) {
                lockManager.removeBioWrap()
                SessionKey.clear()
                onFailed("Biometric changed. Re-enroll in ThaLock.")
            } else {
                onFailed("Biometric unavailable: ${t.localizedMessage ?: "unknown error"}")
            }
            return
        }
        if (cipher == null) {
            onFailed("Open ThaLock to set up biometric first")
            return
        }
        biometricHelper.authenticate(
            activity = this,
            cryptoObject = BiometricPrompt.CryptoObject(cipher),
            onSuccess = { authorized ->
                val ok = try {
                    lockManager.unwrapWithBioCipher(authorized)
                } catch (t: Throwable) {
                    if (lockManager.isBioKeyInvalidated(t)) {
                        lockManager.removeBioWrap()
                        SessionKey.clear()
                        onFailed("Biometric changed. Re-enroll in ThaLock.")
                        return@authenticate
                    }
                    false
                }
                if (ok) onUnlocked() else onFailed("Could not unlock vault")
            },
            onError = { onFailed(it) }
        )
    }

    private fun shareDocument(document: Document) {
        val file = DocumentFileGenerator.generateTextFile(this, document)
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.fileprovider",
            file
        )

        val resultIntent = Intent().apply {
            data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        DocumentFileGenerator.cleanCache(this)
    }
}

@Composable
private fun PickerAuthScreen(
    lockMethod: LockMethod,
    error: String?,
    onBiometricAuth: () -> Unit,
    onPinSubmit: (String) -> Unit,
    onCancel: () -> Unit
) {
    var pin by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Authenticate to share",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Verify your identity to access vault documents",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            when (lockMethod) {
                LockMethod.BIOMETRIC -> {
                    Button(onClick = onBiometricAuth) {
                        Icon(Icons.Default.Fingerprint, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Authenticate")
                    }
                }
                LockMethod.PIN -> {
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            if (it.length <= AppLockManager.MAX_PIN_LENGTH && it.all { c -> c.isDigit() }) {
                                pin = it
                            }
                        },
                        label = { Text("Enter PIN") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { onPinSubmit(pin) },
                        enabled = pin.length >= AppLockManager.MIN_PIN_LENGTH
                    ) {
                        Text("Unlock")
                    }
                }
            }

            error?.let {
                Spacer(modifier = Modifier.height(16.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun DocumentSelectionScreen(
    onDocumentSelected: (Document) -> Unit,
    onCancel: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    var documents by remember { mutableStateOf<List<Document>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        documents = withContext(Dispatchers.IO) {
            db.documentDao().getAllDocuments().first()
        }
        isLoading = false
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Select a Document") },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (documents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No documents in vault",
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Open ThaLock to add documents first",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(paddingValues)
            ) {
                items(documents, key = { it.id }) { document ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDocumentSelected(document) }
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = document.title,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = document.country
                                        ?.let { "${it.displayName} - ${document.documentType.displayName}" }
                                        ?: document.documentType.displayName,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
