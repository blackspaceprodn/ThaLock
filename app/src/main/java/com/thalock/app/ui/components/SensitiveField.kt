package com.thalock.app.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.thalock.app.data.model.DocumentField
import com.thalock.app.ui.theme.LocalSpacing
import com.thalock.app.util.MaskUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Process-wide scope for clipboard auto-clear timers. A composable's
 * [rememberCoroutineScope] is cancelled as soon as the user navigates away,
 * which would leave a copied secret in the clipboard indefinitely. Using an
 * application-lifetime scope ensures the 60s wipe fires even after the screen
 * that triggered the copy is gone.
 */
private val ClipboardScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

@Composable
fun SensitiveFieldRow(
    field: DocumentField,
    modifier: Modifier = Modifier
) {
    var revealed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val spacing = LocalSpacing.current

    val fieldDescription = if (field.isSensitive && !revealed) {
        "${field.label}, masked, tap to reveal"
    } else {
        "${field.label}, ${field.value}"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = spacing.sm)
            .semantics { contentDescription = fieldDescription },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = field.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            AnimatedContent(
                targetState = if (field.isSensitive && !revealed) {
                    MaskUtils.mask(field.value)
                } else {
                    field.value
                },
                transitionSpec = {
                    fadeIn(animationSpec = tween(200)) togetherWith
                            fadeOut(animationSpec = tween(200))
                },
                label = "field_reveal"
            ) { displayValue ->
                Text(
                    text = displayValue,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (field.isSensitive) {
            IconButton(
                onClick = { revealed = !revealed },
                modifier = Modifier.size(spacing.touchTarget)
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Outlined.VisibilityOff
                    else Icons.Outlined.Visibility,
                    contentDescription = if (revealed) "Hide ${field.label}" else "Show ${field.label}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        IconButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText(field.label, field.value)

                // Mark as sensitive on Android 13+ so it won't appear in clipboard history
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    clip.description.extras = PersistableBundle().apply {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                }

                clipboard.setPrimaryClip(clip)
                hapticTick(context)
                Toast.makeText(context, "${field.label} copied (clears in 60s)", Toast.LENGTH_SHORT).show()

                // Auto-clear clipboard after 60 seconds. Runs on a process-wide
                // scope so it survives navigation away from this composable.
                val copiedValue = field.value
                ClipboardScope.launch {
                    delay(60_000)
                    try {
                        val current = clipboard.primaryClip
                        if (current != null && current.itemCount > 0) {
                            val currentText = current.getItemAt(0).text?.toString()
                            if (currentText == copiedValue) {
                                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                            }
                        }
                    } catch (_: Exception) { }
                }
            },
            modifier = Modifier.size(spacing.touchTarget)
        ) {
            Icon(
                imageVector = Icons.Outlined.ContentCopy,
                contentDescription = "Copy ${field.label}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun hapticTick(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator.vibrate(
                VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            vibrator.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    } catch (_: Exception) { }
}
