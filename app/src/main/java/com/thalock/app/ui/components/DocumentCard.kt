package com.thalock.app.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.thalock.app.data.model.Country
import com.thalock.app.data.model.Document
import com.thalock.app.ui.theme.CountryColors
import com.thalock.app.ui.theme.LocalSpacing
import com.thalock.app.util.MaskUtils

@Composable
fun DocumentCard(
    document: Document,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val (startColor, endColor) = gradientForCountry(document.country ?: Country.OTHER)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessLow),
        label = "card_scale"
    )

    val cardDescription = "${document.title}, ${document.documentType.displayName}" +
            (document.country?.let { ", ${it.displayName}" } ?: "")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .scale(scale)
            .semantics { contentDescription = cardDescription },
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp,
            pressedElevation = 2.dp
        ),
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            startColor,
                            startColor.copy(alpha = 0.85f),
                            endColor
                        )
                    )
                )
                .padding(spacing.cardPadding + 4.dp)
        ) {
            // Top row: country code + document type badge
            Row(
                modifier = Modifier.align(Alignment.TopStart),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(spacing.sm)
            ) {
                if (document.country != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(alpha = 0.25f)
                    ) {
                        Text(
                            text = document.country.shortLabel,
                            color = Color.White,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = document.documentType.displayName,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }

            // Center: document title
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 16.dp)
            ) {
                Text(
                    text = document.title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2
                )
            }

            // Bottom row: masked preview + country name
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                val previewField = document.fields.firstOrNull {
                    it.isSensitive && it.value.isNotBlank()
                }
                if (previewField != null) {
                    Text(
                        text = "${previewField.label}: ${MaskUtils.mask(previewField.value)}",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Spacer(modifier = Modifier)
                }

                Text(
                    text = document.country?.displayName ?: "",
                    color = Color.White.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun gradientForCountry(country: Country): Pair<Color, Color> = when (country) {
    Country.INDIA -> CountryColors.IndiaStart to CountryColors.IndiaEnd
    Country.UAE -> CountryColors.UAEStart to CountryColors.UAEEnd
    Country.SINGAPORE -> CountryColors.SingaporeStart to CountryColors.SingaporeEnd
    Country.UK -> CountryColors.UKStart to CountryColors.UKEnd
    Country.USA -> CountryColors.USAStart to CountryColors.USAEnd
    Country.OTHER -> CountryColors.OtherStart to CountryColors.OtherEnd
    else -> CountryColors.OtherStart to CountryColors.OtherEnd
}
