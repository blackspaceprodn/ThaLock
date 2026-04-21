package com.thalock.app.ui.screens.document

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.model.DocumentTemplate
import com.thalock.app.data.model.DocumentType
import com.thalock.app.ui.components.SensitiveFieldRow
import com.thalock.app.ui.theme.CategoryColors
import com.thalock.app.ui.theme.LocalSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentDetailScreen(
    documentId: Long,
    onBack: () -> Unit,
    viewModel: DocumentDetailViewModel = viewModel()
) {
    val document by viewModel.document.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val editedFields by viewModel.editedFields.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showAddFieldDialog by remember { mutableStateOf(false) }
    val spacing = LocalSpacing.current

    LaunchedEffect(documentId) {
        viewModel.loadDocument(documentId)
    }

    val doc = document

    if (doc == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(spacing.md))
                Text(
                    "Loading document...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = spacing.md, vertical = spacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(spacing.touchTarget)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back"
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        "ThaLock.",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(end = spacing.md)
                    )
                }

                // Hero card
                HeroDocumentCard(
                    title = doc.title,
                    documentType = doc.documentType,
                    countryLabel = doc.country?.shortLabel,
                    issueYear = extractIssueYear(doc.fields),
                    modifier = Modifier.padding(horizontal = spacing.screenHorizontal)
                )

                Spacer(modifier = Modifier.height(spacing.lg))

                // Edit/view header
                AnimatedVisibility(
                    visible = isEditing,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = spacing.screenHorizontal),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Editing",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                            TextButton(onClick = { viewModel.cancelEditing() }) {
                                Text("Cancel")
                            }
                            FilledTonalButton(onClick = { viewModel.saveEdits() }) {
                                Text("Save")
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = !isEditing,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Text(
                        "Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal)
                    )
                }

                Spacer(modifier = Modifier.height(spacing.md))

                val fieldsToShow = if (isEditing) editedFields else doc.fields
                val templateFieldCount = DocumentTemplate.fieldsFor(doc.documentType).size

                if (isEditing) {
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(spacing.fieldGap)
                    ) {
                        fieldsToShow.forEachIndexed { index, field ->
                            val isUserAdded = index >= templateFieldCount
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = editedFields.getOrNull(index)?.value ?: "",
                                    onValueChange = { viewModel.updateField(index, it) },
                                    label = {
                                        Text(
                                            if (field.isSensitive) "${field.label} (sensitive)"
                                            else field.label
                                        )
                                    },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isUserAdded) {
                                    IconButton(
                                        onClick = { viewModel.removeField(index) },
                                        modifier = Modifier.size(spacing.touchTarget)
                                    ) {
                                        Icon(
                                            Icons.Outlined.RemoveCircle,
                                            contentDescription = "Remove ${field.label}",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        OutlinedButton(
                            onClick = { showAddFieldDialog = true },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(spacing.touchTarget)
                        ) {
                            Icon(Icons.Outlined.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(spacing.sm))
                            Text("Add Field")
                        }
                    }
                } else {
                    // View mode — each field is its own rounded card
                    Column(
                        modifier = Modifier.padding(horizontal = spacing.screenHorizontal),
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        fieldsToShow.forEach { field ->
                            if (field.value.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SensitiveFieldRow(
                                        field = field,
                                        modifier = Modifier.padding(
                                            horizontal = spacing.cardPadding,
                                            vertical = 2.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xxl))
            }

            // Bottom action bar — Edit + circular trash only (Copy all removed per spec)
            AnimatedVisibility(
                visible = !isEditing,
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = spacing.screenHorizontal,
                                vertical = spacing.md
                            ),
                        horizontalArrangement = Arrangement.spacedBy(spacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = { viewModel.startEditing() },
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.xs))
                            Text("Edit", fontWeight = FontWeight.SemiBold)
                        }

                        // Circular delete
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        ) {
                            IconButton(
                                onClick = { showDeleteDialog = true },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = "Delete document",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddFieldDialog) {
        var fieldLabel by remember { mutableStateOf("") }
        var isSensitive by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAddFieldDialog = false },
            title = { Text("Add Field") },
            text = {
                Column {
                    OutlinedTextField(
                        value = fieldLabel,
                        onValueChange = { fieldLabel = it },
                        label = { Text("Field Name") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(spacing.md))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = isSensitive,
                            onCheckedChange = { isSensitive = it }
                        )
                        Spacer(modifier = Modifier.width(spacing.xs))
                        Text(
                            "Sensitive field (masked by default)",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        if (fieldLabel.isNotBlank()) {
                            viewModel.addCustomField(fieldLabel, isSensitive)
                            showAddFieldDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddFieldDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Delete Document") },
            text = {
                Text(
                    "This will permanently remove this document from your vault. This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteDocument { onBack() }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun HeroDocumentCard(
    title: String,
    documentType: DocumentType,
    countryLabel: String?,
    issueYear: String?,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val category = DocumentCategory.forDocumentType(documentType)
    val bg = when (category) {
        DocumentCategory.IDENTITY -> CategoryColors.IdentityTile
        DocumentCategory.FINANCIAL -> CategoryColors.FinancialTile
        DocumentCategory.INSURANCE -> CategoryColors.InsuranceTile
    }
    // Softer tint to match screenshots (lavender hero card)
    val heroBg = Color(0xFFD6CFFF)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1.8f)
            .clip(RoundedCornerShape(26.dp))
            .background(if (category == DocumentCategory.IDENTITY) heroBg else bg)
    ) {
        // Decorative blob
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(
                    alpha = if (category == DocumentCategory.IDENTITY) 0.35f else 0.12f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.82f, size.height * 0.5f)
            )
        }

        val contentColor =
            if (category == DocumentCategory.IDENTITY) Color(0xFF1F1A4D) else Color.White

        Column(modifier = Modifier.padding(spacing.cardPadding + 4.dp)) {
            Text(
                category.displayName.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor.copy(alpha = 0.75f),
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                title,
                style = MaterialTheme.typography.displaySmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                if (countryLabel != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.5f)
                    ) {
                        Text(
                            countryLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                if (issueYear != null) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = Color.White.copy(alpha = 0.5f)
                    ) {
                        Text(
                            "Issued $issueYear",
                            style = MaterialTheme.typography.labelMedium,
                            color = contentColor,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

// Heuristic: pull a year out of an "issue date" / "date of issue" field if present.
private fun extractIssueYear(fields: List<com.thalock.app.data.model.DocumentField>): String? {
    val issueField = fields.firstOrNull { f ->
        val k = f.key.lowercase()
        val l = f.label.lowercase()
        ("issue" in k || "issue" in l) && f.value.isNotBlank()
    } ?: return null

    val year = Regex("(19|20)\\d{2}").find(issueField.value)?.value
    if (year != null) return year

    // Try parsing as epoch millis fallback
    val asLong = issueField.value.toLongOrNull()
    if (asLong != null) {
        return SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(asLong))
    }
    return null
}
