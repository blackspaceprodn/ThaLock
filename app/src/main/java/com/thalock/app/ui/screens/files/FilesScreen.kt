package com.thalock.app.ui.screens.files

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.model.UploadedFile
import com.thalock.app.ui.theme.CategoryColors
import com.thalock.app.ui.theme.LocalSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    viewModel: FilesViewModel = viewModel()
) {
    val grouped by viewModel.groupedFiles.collectAsState()
    val files by viewModel.files.collectAsState()
    val pending by viewModel.pendingUpload.collectAsState()
    val spacing = LocalSpacing.current
    val context = LocalContext.current

    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, mime) = resolveFileMetadata(context, uri)
            viewModel.stageUpload(uri, name, mime)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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
                "Files",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(spacing.lg))

            if (files.isEmpty()) {
                EmptyFilesState(onUploadClick = { pickFileLauncher.launch("*/*") })
            } else {
                DocumentCategory.entries.forEach { category ->
                    val bucket = grouped[category].orEmpty()
                    if (bucket.isNotEmpty()) {
                        CategorySection(
                            category = category,
                            files = bucket,
                            onDelete = { viewModel.deleteFile(it) }
                        )
                        Spacer(modifier = Modifier.height(spacing.sectionGap))
                    }
                }
            }

            Spacer(modifier = Modifier.height(120.dp))
        }

        // FAB overlay
        FloatingActionButton(
            onClick = { pickFileLauncher.launch("*/*") },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.screenHorizontal)
                .size(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Upload file")
        }
    }

    // Category prompt (always shown on upload)
    if (pending != null) {
        CategoryPromptDialog(
            fileName = pending!!.displayName,
            onPick = { viewModel.confirmCategory(it) },
            onDismiss = { viewModel.cancelPendingUpload() }
        )
    }
}

@Composable
private fun CategorySection(
    category: DocumentCategory,
    files: List<UploadedFile>,
    onDelete: (UploadedFile) -> Unit
) {
    val spacing = LocalSpacing.current
    val tile = tileColor(category)

    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(tile)
        )
        Spacer(modifier = Modifier.width(spacing.sm))
        Text(
            category.displayName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(spacing.sm))
        Text(
            "· ${files.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(modifier = Modifier.height(spacing.md))

    Column(verticalArrangement = Arrangement.spacedBy(spacing.sm)) {
        files.forEach { file ->
            FileRow(file = file, onDelete = { onDelete(file) })
        }
    }
}

@Composable
private fun FileRow(file: UploadedFile, onDelete: () -> Unit) {
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tileColor(file.category).copy(alpha = 0.15f),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        fileIcon(file.mimeType),
                        contentDescription = null,
                        tint = tileColor(file.category),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(spacing.cardGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${formatSize(file.sizeBytes)} · ${formatDate(file.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete ${file.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CategoryPromptDialog(
    fileName: String,
    onPick: (DocumentCategory) -> Unit,
    onDismiss: () -> Unit
) {
    val spacing = LocalSpacing.current
    var selected by remember { mutableStateOf<DocumentCategory?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a category") },
        text = {
            Column {
                Text(
                    "Which category does \"$fileName\" belong to?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(spacing.md))
                DocumentCategory.entries.forEach { category ->
                    val isSelected = selected == category
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) tileColor(category)
                        else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { selected = category }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(spacing.md)
                        ) {
                            Icon(
                                categoryIcon(category),
                                contentDescription = null,
                                tint = if (isSelected) Color.White
                                else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(spacing.md))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    category.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (isSelected) Color.White
                                    else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    category.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) Color.White.copy(alpha = 0.85f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selected?.let(onPick) },
                enabled = selected != null
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun EmptyFilesState(onUploadClick: () -> Unit) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = spacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(88.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.UploadFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.md))
            Text(
                "No files yet",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                "Upload scans and documents. You'll be asked to pick a category each time.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(spacing.md))
            Button(
                onClick = onUploadClick,
                shape = RoundedCornerShape(999.dp)
            ) {
                Icon(Icons.Outlined.UploadFile, contentDescription = null)
                Spacer(modifier = Modifier.width(spacing.xs))
                Text("Upload a file")
            }
        }
    }
}

private fun tileColor(category: DocumentCategory): Color = when (category) {
    DocumentCategory.IDENTITY -> CategoryColors.IdentityTile
    DocumentCategory.FINANCIAL -> CategoryColors.FinancialTile
    DocumentCategory.INSURANCE -> CategoryColors.InsuranceTile
}

private fun categoryIcon(category: DocumentCategory): ImageVector = when (category) {
    DocumentCategory.IDENTITY -> Icons.Outlined.Badge
    DocumentCategory.FINANCIAL -> Icons.Outlined.AccountBalance
    DocumentCategory.INSURANCE -> Icons.Outlined.Shield
}

private fun fileIcon(mime: String?): ImageVector {
    if (mime == null) return Icons.Outlined.InsertDriveFile
    return when {
        mime.startsWith("image/") -> Icons.Outlined.Image
        mime == "application/pdf" -> Icons.Outlined.PictureAsPdf
        mime.startsWith("text/") -> Icons.Outlined.Description
        else -> Icons.Outlined.InsertDriveFile
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
}

private fun formatDate(timestamp: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(timestamp))

private fun resolveFileMetadata(
    context: android.content.Context,
    uri: Uri
): Pair<String, String?> {
    var name = "upload"
    val mime = context.contentResolver.getType(uri)
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            val value = cursor.getString(nameIndex)
            if (!value.isNullOrBlank()) name = value
        }
    }
    return name to mime
}
