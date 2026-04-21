package com.thalock.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.biometric.BiometricPrompt
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.thalock.app.security.AppLockManager
import com.thalock.app.security.BiometricHelper
import com.thalock.app.security.LockMethod
import com.thalock.app.security.SafExposurePreference
import com.thalock.app.ui.theme.LocalSpacing
import com.thalock.app.ui.theme.ThemeMode
import com.thalock.app.ui.theme.ThemePreference

@Composable
fun SettingsScreen(
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val lockManager = remember { AppLockManager(context) }
    val biometricHelper = remember { BiometricHelper(context) }
    var currentMethod by remember { mutableStateOf(lockManager.lockMethod) }
    var showPinDialog by remember { mutableStateOf(false) }
    var biometricError by remember { mutableStateOf<String?>(null) }
    var safEnabled by remember { mutableStateOf(SafExposurePreference.isEnabled(context)) }
    val themeMode by ThemePreference.mode.collectAsState()
    val spacing = LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = spacing.screenHorizontal)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "ThaLock.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Text(
            "Settings",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(spacing.lg))

        // Security
        SectionHeader("Security", MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(spacing.md))

        // Lock method card (primary-colored hero)
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(spacing.cardPadding + 4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Outlined.Dialpad,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(spacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Lock method",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            if (currentMethod == LockMethod.BIOMETRIC)
                                "Biometric · App PIN ready"
                            else "App PIN · Biometric ready",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                    Icon(
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.md))

                Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    // Change PIN
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.22f),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showPinDialog = true }
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                "Change PIN",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    // Biometric toggle. Switching to biometric must perform biometric auth
                    // against an ENCRYPT cipher and create the DB-passphrase wrap — flipping
                    // the flag alone would leave the next launch unable to open the DB.
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp, Color.White.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                biometricError = null
                                val activity = context as? FragmentActivity
                                if (activity == null) {
                                    biometricError = "Biometric unavailable"
                                    return@clickable
                                }
                                if (currentMethod == LockMethod.BIOMETRIC) {
                                    // BIOMETRIC -> PIN: only allowed once a PIN exists.
                                    if (!lockManager.isPinSet) {
                                        biometricError = "Set a PIN first"
                                        return@clickable
                                    }
                                    lockManager.lockMethod = LockMethod.PIN
                                    currentMethod = LockMethod.PIN
                                    return@clickable
                                }
                                // PIN -> BIOMETRIC: auth + wrap.
                                if (!biometricHelper.canAuthenticate()) {
                                    biometricError = "No biometric enrolled"
                                    return@clickable
                                }
                                val cipher = try {
                                    lockManager.buildBioEncryptCipher()
                                } catch (t: Throwable) {
                                    if (lockManager.isBioKeyInvalidated(t)) {
                                        lockManager.removeBioWrap()
                                        runCatching { lockManager.buildBioEncryptCipher() }
                                            .getOrNull()
                                    } else null
                                } ?: run {
                                    biometricError = "Biometric unavailable"
                                    return@clickable
                                }
                                biometricHelper.authenticate(
                                    activity = activity,
                                    cryptoObject = BiometricPrompt.CryptoObject(cipher),
                                    onSuccess = { authorized ->
                                        runCatching {
                                            lockManager.wrapWithBioCipher(authorized)
                                            lockManager.lockMethod = LockMethod.BIOMETRIC
                                            currentMethod = LockMethod.BIOMETRIC
                                        }.onFailure {
                                            biometricError = "Could not enable biometric"
                                        }
                                    },
                                    onError = { biometricError = it }
                                )
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Fingerprint,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Text(
                                "Biometric",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        biometricError?.let {
            Spacer(modifier = Modifier.height(spacing.sm))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(modifier = Modifier.height(spacing.md))

        // SAF exposure toggle — controls whether other apps can see the vault
        // as a document source via Storage Access Framework while unlocked.
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(spacing.cardPadding)
            ) {
                Icon(
                    Icons.Outlined.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Expose vault to other apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Lets system pickers (e.g. Gmail attach) list ThaLock documents while unlocked. Turn off to keep the vault fully private.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(spacing.sm))
                Switch(
                    checked = safEnabled,
                    onCheckedChange = {
                        safEnabled = it
                        SafExposurePreference.setEnabled(context, it)
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(spacing.sectionGap))

        // Appearance
        SectionHeader("Appearance", MaterialTheme.colorScheme.tertiary)
        Spacer(modifier = Modifier.height(spacing.md))

        // Theme segmented
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(spacing.cardPadding)) {
                Text(
                    "Theme",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ThemeMode.entries.forEach { option ->
                        val selected = option == themeMode
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else Color.Transparent
                                )
                                .clickable { ThemePreference.setMode(option) }
                                .padding(vertical = 10.dp)
                        ) {
                            Text(
                                option.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) Color.White
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(spacing.sectionGap))

        // About
        SectionHeader("About", MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(spacing.md))

        Surface(
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(modifier = Modifier.padding(spacing.cardPadding)) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(spacing.md))
                Column {
                    Text(
                        "ThaLock",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Version 1.0.0",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        "Your personal offline document vault. All data is stored locally on your device and never leaves it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        "Imagined by a random guy\nBuilt with Claude",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(120.dp))
    }

    if (showPinDialog) {
        ChangePinDialog(
            lockManager = lockManager,
            onDismiss = { showPinDialog = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String, accent: Color) {
    val spacing = LocalSpacing.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent)
        )
        Spacer(modifier = Modifier.width(spacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = accent,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ChangePinDialog(
    lockManager: AppLockManager,
    onDismiss: () -> Unit
) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(if (lockManager.isPinSet) 1 else 2) }
    var error by remember { mutableStateOf<String?>(null) }
    val spacing = LocalSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when (step) {
                    1 -> "Current PIN"
                    2 -> "New PIN"
                    else -> "Confirm New PIN"
                }
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = when (step) {
                        1 -> currentPin
                        2 -> newPin
                        else -> confirmPin
                    },
                    onValueChange = {
                        if (it.length <= AppLockManager.MAX_PIN_LENGTH && it.all { c -> c.isDigit() }) {
                            when (step) {
                                1 -> currentPin = it
                                2 -> newPin = it
                                else -> confirmPin = it
                            }
                            error = null
                        }
                    },
                    label = {
                        Text(
                            when (step) {
                                1 -> "Enter current PIN"
                                2 -> "Enter new ${AppLockManager.MIN_PIN_LENGTH}-digit PIN"
                                else -> "Re-enter new PIN"
                            }
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = {
                when (step) {
                    1 -> {
                        if (lockManager.verifyPin(currentPin)) {
                            step = 2
                        } else {
                            error = "Incorrect PIN"
                            currentPin = ""
                        }
                    }
                    2 -> {
                        if (newPin.length < AppLockManager.MIN_PIN_LENGTH) {
                            error = "PIN must be at least ${AppLockManager.MIN_PIN_LENGTH} digits"
                        } else {
                            step = 3
                        }
                    }
                    else -> {
                        if (newPin == confirmPin) {
                            lockManager.setPin(newPin)
                            onDismiss()
                        } else {
                            error = "PINs don't match"
                            confirmPin = ""
                        }
                    }
                }
            }) {
                Text(if (step < 3) "Next" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
