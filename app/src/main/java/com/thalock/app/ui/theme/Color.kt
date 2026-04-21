package com.thalock.app.ui.theme

import androidx.compose.ui.graphics.Color

// Violet palette
val ThaLockPrimary = Color(0xFF8B7DFF)          // Vivid violet
val ThaLockPrimaryDeep = Color(0xFF6A5AE0)      // Deeper violet for pressed/container
val ThaLockPrimarySoft = Color(0xFFB7ADFF)      // Lavender soft tint
val ThaLockSecondary = Color(0xFF5B6475)        // Slate
val ThaLockTertiary = Color(0xFFC27FFF)         // Purple-pink accent
val ThaLockNeutral = Color(0xFFECEBF4)          // Deepened off-white violet tinted

// Dark surfaces
val SurfaceDark = Color(0xFF0F0F14)
val SurfaceCardDark = Color(0xFF1A1A22)
val SurfaceElevatedDark = Color(0xFF22222C)
val SurfaceLight = ThaLockNeutral
val SurfaceCardLight = Color(0xFFFFFFFF)

// Semantic colors
val Success = Color(0xFF2ECC71)
val Warning = Color(0xFFF39C12)
val Info = Color(0xFF5B9BFF)
val Error = Color(0xFFE05C6B)

// Category accent colors (tinted backgrounds / fills)
object CategoryColors {
    // Identity — vivid violet (matches primary)
    val IdentityStart = Color(0xFF7B68EE)
    val IdentityEnd = Color(0xFF5B4AD6)
    val IdentityTile = Color(0xFF6A5AE0)

    // Financial — crimson / maroon
    val FinancialStart = Color(0xFFB83A4E)
    val FinancialEnd = Color(0xFF8B2B3C)
    val FinancialTile = Color(0xFF8B2B3C)

    // Insurance — slate / teal grey
    val InsuranceStart = Color(0xFF4F5865)
    val InsuranceEnd = Color(0xFF3A414C)
    val InsuranceTile = Color(0xFF3A414C)
}

// Country card gradient colors (unused visually in new design but kept for compat)
object CountryColors {
    val IndiaStart = Color(0xFFFF9933)
    val IndiaEnd = Color(0xFF138808)

    val UAEStart = Color(0xFF00732F)
    val UAEEnd = Color(0xFFCE1126)

    val SingaporeStart = Color(0xFFEE2536)
    val SingaporeEnd = Color(0xFFC41E3A)

    val UKStart = Color(0xFF00247D)
    val UKEnd = Color(0xFFCF142B)

    val USAStart = Color(0xFF3C3B6E)
    val USAEnd = Color(0xFFB22234)

    val OtherStart = Color(0xFF636E72)
    val OtherEnd = Color(0xFF2D3436)
}

// Text
val TextPrimaryDark = Color(0xFFEDEAFF)
val TextSecondaryDark = Color(0xFF9A97AE)
val TextPrimaryLight = Color(0xFF1A1A24)
val TextSecondaryLight = Color(0xFF5A5A6C)
val TextOnCard = Color(0xFFFFFFFF)
