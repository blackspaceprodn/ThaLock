@file:Suppress("FunctionName")
package com.thalock.app

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Pin
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.thalock.app.security.AppLockManager
import com.thalock.app.security.LockMethod
import com.thalock.app.ui.theme.LocalSpacing
import com.thalock.app.ui.theme.ThaLockPrimary
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

// ────────────────────────────────────────────────────────────────────────────
// FlowerShape — scalloped petal ring drawn on Canvas
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun FlowerShape(
    color: Color,
    sizeDp: Float,
    petals: Int = 8,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(sizeDp.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = size.minDimension / 2f
        val path = Path()
        val angleStep = (2 * Math.PI / petals).toFloat()
        val petalDepth = r * 0.18f

        for (i in 0 until petals) {
            val startAngle = i * angleStep
            val midAngle = startAngle + angleStep / 2f
            val endAngle = startAngle + angleStep

            val sx = cx + r * cos(startAngle)
            val sy = cy + r * sin(startAngle)
            val mx = cx + (r - petalDepth) * cos(midAngle)
            val my = cy + (r - petalDepth) * sin(midAngle)
            val ex = cx + r * cos(endAngle)
            val ey = cy + r * sin(endAngle)

            if (i == 0) path.moveTo(sx, sy)
            path.quadraticBezierTo(mx, my, ex, ey)
        }
        path.close()
        drawPath(path, color, style = Fill)
    }
}

// ────────────────────────────────────────────────────────────────────────────
// PIN dots (theme-aware)
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun PinDotsRow(length: Int, total: Int = 4) {
    val colors = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        for (i in 0 until total) {
            val filled = i < length
            val s by animateFloatAsState(
                if (filled) 1f else 0.85f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                label = "dot_scale"
            )
            Box(
                modifier = Modifier
                    .size(if (filled) 22.dp else 16.dp)
                    .scale(s)
                    .then(
                        if (filled) Modifier
                            .background(colors.primary, RoundedCornerShape(7.dp))
                        else Modifier
                            .border(2.dp, colors.outline, CircleShape)
                    )
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Keypad button (theme-aware with variant)
// ────────────────────────────────────────────────────────────────────────────

enum class KeypadVariant { PRIMARY, SECONDARY, TERTIARY }

@Composable
fun KeypadBtn(
    digit: String? = null,
    icon: @Composable (() -> Unit)? = null,
    onClick: () -> Unit,
    enabled: Boolean = true,
    variant: KeypadVariant = KeypadVariant.PRIMARY,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    var pressed by remember { mutableStateOf(false) }
    val cornerRadius by animateDpAsState(
        if (pressed) 20.dp else 32.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "keypad_corner"
    )
    val scale by animateFloatAsState(
        if (pressed) 0.94f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "keypad_scale"
    )

    val bg = when {
        !enabled -> colors.surfaceContainerHigh.copy(alpha = 0.4f)
        variant == KeypadVariant.SECONDARY -> colors.secondaryContainer
        variant == KeypadVariant.TERTIARY -> colors.tertiaryContainer
        else -> colors.surfaceContainerHigh
    }
    val fg = when {
        !enabled -> colors.onSurface.copy(alpha = 0.3f)
        variant == KeypadVariant.SECONDARY -> colors.onSecondaryContainer
        variant == KeypadVariant.TERTIARY -> colors.onTertiaryContainer
        else -> colors.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(64.dp)
            .scale(scale)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg)
            .clickable(enabled = enabled) {
                pressed = true
                onClick()
            }
    ) {
        if (icon != null) {
            icon()
        } else if (digit != null) {
            Text(
                digit,
                color = fg,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
    LaunchedEffect(pressed) {
        if (pressed) { delay(120); pressed = false }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// PinPad (theme-aware)
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun PinPad(
    onDigit: (String) -> Unit,
    onDelete: () -> Unit,
    onSubmit: () -> Unit = {},
    submitEnabled: Boolean = false,
    showSubmit: Boolean = false,
    enabled: Boolean = true,
    onBiometric: (() -> Unit)? = null
) {
    val colors = MaterialTheme.colorScheme
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9")
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        rows.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { d ->
                    KeypadBtn(
                        digit = d,
                        onClick = { if (enabled) onDigit(d) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Bottom-left: biometric / submit / empty
            when {
                onBiometric != null -> KeypadBtn(
                    icon = {
                        Icon(
                            Icons.Outlined.Fingerprint,
                            contentDescription = "Biometric",
                            tint = colors.onTertiaryContainer,
                            modifier = Modifier.size(28.dp)
                        )
                    },
                    onClick = { if (enabled) onBiometric() },
                    enabled = enabled,
                    variant = KeypadVariant.TERTIARY,
                    modifier = Modifier.weight(1f)
                )
                showSubmit -> KeypadBtn(
                    icon = {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Submit",
                            tint = if (submitEnabled) colors.onSecondaryContainer
                                   else colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(26.dp)
                        )
                    },
                    onClick = { if (enabled && submitEnabled) onSubmit() },
                    enabled = enabled && submitEnabled,
                    variant = KeypadVariant.SECONDARY,
                    modifier = Modifier.weight(1f)
                )
                else -> Spacer(modifier = Modifier.weight(1f))
            }
            KeypadBtn(
                digit = "0",
                onClick = { if (enabled) onDigit("0") },
                enabled = enabled,
                modifier = Modifier.weight(1f)
            )
            KeypadBtn(
                icon = {
                    Icon(
                        Icons.Outlined.Backspace,
                        contentDescription = "Delete",
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                },
                onClick = { if (enabled) onDelete() },
                enabled = enabled,
                variant = KeypadVariant.SECONDARY,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Step dots (for PIN setup)
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun StepDot(active: Boolean) {
    val w by animateDpAsState(
        if (active) 24.dp else 8.dp,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "step_dot_width"
    )
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(3.dp),
        color = if (active) colors.primary else colors.outline,
        modifier = Modifier
            .width(w)
            .height(6.dp)
    ) {}
}

// ────────────────────────────────────────────────────────────────────────────
// Lock Screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun LockScreen(
    lockMethod: LockMethod,
    lockManager: AppLockManager,
    error: String?,
    onBiometricAuth: () -> Unit,
    onPinSubmit: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val maxLen = AppLockManager.MAX_PIN_LENGTH
    val minLen = AppLockManager.MIN_PIN_LENGTH

    // Lockout countdown
    var lockoutSeconds by remember { mutableIntStateOf(0) }
    val isLockedOut = lockoutSeconds > 0

    LaunchedEffect(lockManager.isLockedOut) {
        if (lockManager.isLockedOut) {
            while (true) {
                val remaining = lockManager.lockoutRemainingMs
                if (remaining <= 0) { lockoutSeconds = 0; break }
                lockoutSeconds = ((remaining + 999) / 1000).toInt()
                delay(1000)
            }
        } else { lockoutSeconds = 0 }
    }

    // Shake on error
    val shake = remember { Animatable(0f) }
    LaunchedEffect(error) {
        if (error != null) {
            pin = ""
            shake.snapTo(0f)
            repeat(4) { shake.animateTo(if (it % 2 == 0) 12f else -12f, tween(60)) }
            shake.animateTo(0f, tween(60))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.primaryContainer,
                        colors.surface
                    ),
                    center = Offset(Float.POSITIVE_INFINITY * 0.5f, 0f),
                    radius = 1400f
                )
            )
    ) {
        // Decorative flower blurs
        Box(
            modifier = Modifier
                .offset(x = (-60).dp, y = (-60).dp)
                .alpha(0.4f)
        ) {
            FlowerShape(
                color = colors.primary,
                sizeDp = 300f,
                petals = 8
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-80).dp, y = 80.dp)
                .alpha(0.2f)
        ) {
            FlowerShape(
                color = colors.tertiary,
                sizeDp = 260f,
                petals = 6
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = spacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.5f))

            // Lock badge
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Welcome back",
                style = MaterialTheme.typography.displaySmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Enter your PIN to unlock",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Lockout warning
            if (isLockedOut) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.errorContainer,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            "Too many attempts",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.onErrorContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Try again in ${lockoutSeconds}s",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onErrorContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            // PIN dots with shake
            Box(modifier = Modifier.offset(x = shake.value.dp)) {
                PinDotsRow(length = pin.length, total = maxOf(minLen, pin.length))
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Error / retry feedback. Never show a PIN hint: a vault lock screen must
            // not leak information about the PIN, even a placeholder.
            Text(
                text = when {
                    error != null -> error
                    shake.value != 0f -> "Try again"
                    else -> " "
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (error != null) colors.error else colors.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.weight(1f))

            PinPad(
                onDigit = { d ->
                    if (!isLockedOut && pin.length < maxLen) pin += d
                },
                onDelete = {
                    if (!isLockedOut && pin.isNotEmpty()) pin = pin.dropLast(1)
                },
                onSubmit = {
                    if (!isLockedOut && pin.length >= minLen) { onPinSubmit(pin); pin = "" }
                },
                submitEnabled = pin.length >= minLen && !isLockedOut,
                showSubmit = lockMethod != LockMethod.BIOMETRIC,
                enabled = !isLockedOut,
                onBiometric = if (lockMethod == LockMethod.BIOMETRIC) onBiometricAuth else null
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// PIN Setup Screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun PinSetupScreen(
    onPinConfirmed: (String) -> Unit,
    onBack: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var step by remember { mutableIntStateOf(1) }
    var error by remember { mutableStateOf<String?>(null) }
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme

    val currentPin = if (step == 1) pin else confirmPin
    val maxLen = AppLockManager.MAX_PIN_LENGTH
    val minLen = AppLockManager.MIN_PIN_LENGTH

    val submit: () -> Unit = {
        if (step == 1) {
            if (pin.length < minLen) error = "PIN must be at least $minLen digits"
            else { step = 2; error = null }
        } else {
            if (pin == confirmPin) onPinConfirmed(pin)
            else { error = "PINs don't match"; confirmPin = "" }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        colors.primaryContainer,
                        colors.surface
                    ),
                    center = Offset(Float.POSITIVE_INFINITY * 0.5f, 0f),
                    radius = 1400f
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = spacing.screenHorizontal),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        if (step == 2) { step = 1; confirmPin = ""; error = null }
                        else onBack()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = colors.onSurface
                    )
                ) {
                    Text("Back", fontWeight = FontWeight.SemiBold)
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StepDot(active = true)
                    StepDot(active = step >= 2)
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(64.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Lock badge
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(spacing.lg))

            Text(
                text = if (step == 1) "Create your PIN" else "Confirm your PIN",
                style = MaterialTheme.typography.displaySmall,
                color = colors.onSurface,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.5).sp,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(spacing.xs))

            Text(
                text = if (step == 1) "Use at least $minLen digits (max $maxLen)"
                       else "Enter the same PIN again",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(spacing.xl))

            PinDotsRow(length = currentPin.length, total = maxOf(minLen, currentPin.length))

            error?.let {
                Spacer(modifier = Modifier.height(spacing.sm))
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            PinPad(
                onDigit = { d ->
                    if (currentPin.length < maxLen) {
                        if (step == 1) pin += d else confirmPin += d
                        error = null
                        if (step == 2 && confirmPin.length == pin.length) submit()
                    }
                },
                onDelete = {
                    if (step == 1 && pin.isNotEmpty()) pin = pin.dropLast(1)
                    else if (step == 2 && confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    error = null
                },
                onSubmit = submit,
                submitEnabled = currentPin.length >= minLen,
                showSubmit = step == 1,
                enabled = true
            )

            Spacer(modifier = Modifier.height(spacing.lg))
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Onboarding Screen
// ────────────────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    canUseBiometric: Boolean,
    onBiometricChosen: () -> Unit,
    onPinSet: (String) -> Unit
) {
    var showPinSetup by remember { mutableStateOf(false) }
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme

    Crossfade(targetState = showPinSetup, label = "onboarding_step") { isPinSetup ->
        if (isPinSetup) {
            PinSetupScreen(
                onPinConfirmed = onPinSet,
                onBack = { showPinSetup = false }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.surface)
            ) {
                // Decorative circles
                Box(
                    modifier = Modifier
                        .size(300.dp)
                        .offset(x = (-80).dp, y = (-60).dp)
                        .alpha(0.06f)
                        .background(colors.primary, CircleShape)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = spacing.screenHorizontal + 8.dp)
                        .padding(top = 80.dp, bottom = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top section
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = colors.primaryContainer,
                            modifier = Modifier.size(96.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = colors.onPrimaryContainer,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.lg))

                        Text(
                            text = "ThaLock",
                            style = MaterialTheme.typography.displayLarge,
                            color = colors.onSurface,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        )

                        Spacer(modifier = Modifier.height(spacing.sm))

                        Text(
                            text = "Your personal offline\ndocument vault",
                            style = MaterialTheme.typography.bodyLarge,
                            color = colors.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            lineHeight = 24.sp
                        )
                    }

                    // Bottom section: security options
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SECURE YOUR VAULT",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            letterSpacing = 2.sp
                        )

                        Spacer(modifier = Modifier.height(spacing.lg))

                        // Biometric option
                        if (canUseBiometric) {
                            Button(
                                onClick = onBiometricChosen,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.primary,
                                    contentColor = colors.onPrimary
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Fingerprint,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(horizontalAlignment = Alignment.Start) {
                                    Text(
                                        "Use Device Authentication",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "Fingerprint, face, or screen lock",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.onPrimary.copy(alpha = 0.7f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // PIN option
                        OutlinedButton(
                            onClick = { showPinSetup = true },
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Pin,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    "Set App PIN",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Create a 4-6 digit PIN",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(spacing.lg))

                        // Trust indicators
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.alpha(0.6f)
                        ) {
                            TrustBadge("Offline Only")
                            TrustBadge("Encrypted")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrustBadge(label: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            Icons.Default.Check,
            contentDescription = null,
            tint = colors.primary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant
        )
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Intro Slides (onboarding)
// ────────────────────────────────────────────────────────────────────────────

data class IntroSlide(
    val kind: String,
    val kicker: String,
    val title: String,
    val titleAccent: String,
    val body: String
)

@Composable
fun IntroSlides(onDone: () -> Unit) {
    val spacing = LocalSpacing.current
    val colors = MaterialTheme.colorScheme
    val slides = remember {
        listOf(
            IntroSlide("welcome", "WELCOME TO", "ThaLock.", "Your personal vault.",
                "One private place for every document that matters — IDs, cards, bank details, insurance."),
            IntroSlide("privacy", "PRIVACY FIRST", "Stored on", "this device.",
                "Your documents never leave your phone. No servers. No cloud. No accounts."),
            IntroSlide("quickcopy", "FAST ACCESS", "Tap to copy,", "every field.",
                "Need your IFSC at checkout? Card number mid-call? One tap and it's on your clipboard."),
            IntroSlide("organize", "ORGANIZED", "Folders for", "every moment.",
                "Identity, Financial, Insurance — already laid out. Add your own. Search anything instantly.")
        )
    }
    var index by remember { mutableIntStateOf(0) }
    val isLast = index == slides.lastIndex
    val slide = slides[index]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            // Skip button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal),
                horizontalArrangement = Arrangement.End
            ) {
                if (!isLast) {
                    TextButton(
                        onClick = onDone,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = colors.onSurfaceVariant
                        )
                    ) {
                        Text("Skip", fontWeight = FontWeight.SemiBold)
                    }
                } else {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }

            // Visual stage
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 20.dp)
            ) {
                Crossfade(targetState = index, label = "intro_stage") { i ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when (slides[i].kind) {
                            "welcome" -> StageWelcome()
                            "privacy" -> StagePrivacy()
                            "quickcopy" -> StageQuickCopy()
                            "organize" -> StageOrganize()
                        }
                    }
                }
            }

            // Copy section
            Crossfade(targetState = index, label = "intro_copy") { i ->
                val s = slides[i]
                Column(
                    modifier = Modifier.padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = 16.dp
                    )
                ) {
                    Text(
                        s.kicker,
                        style = MaterialTheme.typography.labelLarge,
                        color = colors.primary,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        s.title,
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.onSurface,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1).sp,
                        lineHeight = 40.sp
                    )
                    Text(
                        s.titleAccent,
                        style = MaterialTheme.typography.displayMedium,
                        color = colors.primary,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = (-1).sp,
                        lineHeight = 40.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        s.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }

            // Footer: progress dots + CTA
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = spacing.screenHorizontal)
                    .padding(top = 12.dp)
            ) {
                // Progress pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    slides.indices.forEach { i ->
                        val active = i == index
                        val w by animateDpAsState(
                            if (active) 36.dp else 8.dp,
                            spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "intro_pill_width"
                        )
                        Box(
                            modifier = Modifier
                                .width(w)
                                .height(8.dp)
                                .background(
                                    if (i <= index) colors.primary
                                    else colors.surfaceContainerHighest,
                                    RoundedCornerShape(9999.dp)
                                )
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // CTA button
                val btnWidth by animateDpAsState(
                    if (isLast) 200.dp else 64.dp,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                    label = "intro_btn_width"
                )
                val btnCorner by animateDpAsState(
                    if (isLast) 28.dp else 32.dp,
                    label = "intro_btn_corner"
                )

                Button(
                    onClick = { if (isLast) onDone() else index++ },
                    shape = RoundedCornerShape(btnCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    ),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 6.dp
                    ),
                    contentPadding = PaddingValues(horizontal = if (isLast) 28.dp else 0.dp),
                    modifier = Modifier
                        .width(btnWidth)
                        .height(64.dp)
                ) {
                    if (isLast) {
                        Text(
                            "Get started",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Icon(
                        Icons.Outlined.ArrowForward,
                        contentDescription = if (isLast) null else "Next",
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

// ────────────────────────────────────────────────────────────────────────────
// Onboarding stage visuals
// ────────────────────────────────────────────────────────────────────────────

@Composable
private fun StageWelcome() {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(280.dp)
    ) {
        // Orbiting decorative shapes
        // Pink square — top-right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 0.dp, y = (-10).dp)
                .size(40.dp)
                .background(colors.tertiary, RoundedCornerShape(14.dp))
        )
        // Gray circle — bottom-left
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-10).dp, y = 20.dp)
                .size(56.dp)
                .background(colors.secondaryContainer, CircleShape)
        )
        // Small purple square — top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 10.dp, y = 30.dp)
                .size(24.dp)
                .alpha(0.5f)
                .background(colors.primary, RoundedCornerShape(8.dp))
        )

        // Main flower mark
        Box(contentAlignment = Alignment.Center) {
            FlowerShape(
                color = colors.primaryContainer,
                sizeDp = 220f,
                petals = 8,
                modifier = Modifier.alpha(0.95f)
            )
            // Lock icon in center
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.primary,
                shadowElevation = 8.dp,
                modifier = Modifier.size(96.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun StagePrivacy() {
    val colors = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        // Phone
        Box(modifier = Modifier.width(120.dp).height(200.dp)) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = colors.primaryContainer,
                shadowElevation = 12.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Status bar
                    Box(
                        modifier = Modifier
                            .width(56.dp)
                            .height(18.dp)
                            .align(Alignment.CenterHorizontally)
                            .background(
                                colors.primary.copy(alpha = 0.3f),
                                RoundedCornerShape(9.dp)
                            )
                    )
                    // Main area with lock
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(colors.primary, RoundedCornerShape(20.dp))
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    // Bottom bar
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .background(
                                    colors.primary.copy(alpha = 0.4f),
                                    RoundedCornerShape(6.dp)
                                )
                        )
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .background(
                                    colors.primary.copy(alpha = 0.4f),
                                    RoundedCornerShape(6.dp)
                                )
                        )
                    }
                }
            }
            // Checkmark badge
            Surface(
                shape = CircleShape,
                color = colors.primary,
                border = BorderStroke(3.dp, colors.surface),
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 6.dp, y = 6.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Slashed connection with X
        Box(contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(colors.outlineVariant.copy(alpha = 0.6f), CircleShape)
                    )
                }
            }
            Surface(
                shape = CircleShape,
                color = colors.errorContainer,
                border = BorderStroke(3.dp, colors.surface),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        "✕",
                        color = colors.error,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Server (dimmed)
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = colors.surfaceContainerHigh,
            modifier = Modifier
                .width(100.dp)
                .height(140.dp)
                .alpha(0.5f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(3) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(colors.outlineVariant, RoundedCornerShape(10.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(colors.outline, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageQuickCopy() {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(horizontal = 20.dp)
    ) {
        // Debit card
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.primaryContainer,
            shadowElevation = 12.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(modifier = Modifier.padding(18.dp)) {
                Column {
                    Text(
                        "DEBIT CARD",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onPrimaryContainer.copy(alpha = 0.7f),
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "HDFC Platinum",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onPrimaryContainer,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "4210  8834  ••••  ••••",
                        color = colors.onPrimaryContainer,
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 2.sp
                    )
                }
                // Copy button
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.primary,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // "Copied" toast
        Surface(
            shape = RoundedCornerShape(9999.dp),
            color = colors.inverseSurface,
            shadowElevation = 8.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = colors.inversePrimary,
                    modifier = Modifier.size(20.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Copied · 4210 8834 2291 7730",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.inverseOnSurface,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // CVV row
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = colors.surfaceContainerHigh
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CVV",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant
                    )
                    Text(
                        "•••",
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = colors.secondaryContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            tint = colors.onSecondaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StageOrganize() {
    val colors = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .padding(horizontal = 12.dp)
    ) {
        // Identity card — spans full height
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = colors.primaryContainer,
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Decorative flower
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 20.dp, y = 20.dp)
                        .alpha(0.18f)
                ) {
                    FlowerShape(
                        color = colors.onPrimaryContainer,
                        sizeDp = 140f,
                        petals = 6
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Outlined.Badge,
                        contentDescription = null,
                        tint = colors.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "Identity",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.onPrimaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "2 docs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Right column
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.weight(1f)
        ) {
            // Financial
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.tertiaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Outlined.AccountBalance,
                        contentDescription = null,
                        tint = colors.onTertiaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "Financial",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "3 docs",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onTertiaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
            // Insurance
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = colors.secondaryContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        tint = colors.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            "Insurance",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.onSecondaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "1 doc",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSecondaryContainer.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}
