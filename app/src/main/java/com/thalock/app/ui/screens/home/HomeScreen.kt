package com.thalock.app.ui.screens.home

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.ui.theme.CategoryColors
import com.thalock.app.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDocumentClick: (Long) -> Unit,
    onAddClick: () -> Unit,
    onFolderClick: (DocumentCategory) -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val documents by viewModel.documents.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val vaultFolders by viewModel.vaultFolders.collectAsState()
    val recentDocuments by viewModel.recentDocuments.collectAsState()
    val spacing = LocalSpacing.current

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

            // Search bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = {
                    Text(
                        "Search your vault",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = Color.Transparent,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier
                    .fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(spacing.lg))

            if (documents.isEmpty() && searchQuery.isBlank()) {
                EmptyVaultState()
            } else if (documents.isEmpty() && searchQuery.isNotBlank()) {
                EmptySearchState(query = searchQuery)
            } else {
                // Folders section
                Text(
                    "Folders",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(spacing.md))

                FoldersGrid(
                    folders = vaultFolders,
                    onFolderClick = { folder -> onFolderClick(folder.category) },
                    modifier = Modifier
                )

                Spacer(modifier = Modifier.height(spacing.sectionGap))

                // Recents
                if (recentDocuments.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recents",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "See all",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(spacing.md))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing.sm)
                    ) {
                        recentDocuments.forEach { doc ->
                            RecentDocumentRow(
                                document = doc,
                                onClick = { onDocumentClick(doc.id) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(120.dp))
            }
        }

        // FAB overlay
        FloatingActionButton(
            onClick = onAddClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = Color.White,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(spacing.screenHorizontal)
                .size(56.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Document")
        }
    }
}

@Composable
private fun FoldersGrid(
    folders: List<VaultFolder>,
    onFolderClick: (VaultFolder) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    // Build a stable 3-slot view: a primary tile (largest category), plus two smaller tiles
    // for the remaining categories.
    val ordered = DocumentCategory.entries.map { cat ->
        folders.find { it.category == cat } ?: VaultFolder(cat, 0, emptyList())
    }

    val primary = ordered.maxByOrNull { it.count } ?: ordered.first()
    val remaining = ordered.filter { it.category != primary.category }

    // Compute explicit heights so the large tile exactly matches the total height of the
    // two stacked small tiles plus the gap between them — eliminates the alignment drift
    // we had when both sides were driven by independent aspect ratios.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val tileWidth = (maxWidth - spacing.cardGap) / 2
        val smallHeight = tileWidth / 1.6f
        val largeHeight = smallHeight * 2 + spacing.cardGap

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(spacing.cardGap)
        ) {
            FolderTileLarge(
                folder = primary,
                onClick = { onFolderClick(primary) },
                modifier = Modifier
                    .width(tileWidth)
                    .height(largeHeight)
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(spacing.cardGap)
            ) {
                remaining.forEach { folder ->
                    FolderTileSmall(
                        folder = folder,
                        onClick = { onFolderClick(folder) },
                        modifier = Modifier
                            .width(tileWidth)
                            .height(smallHeight)
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderTileLarge(
    folder: VaultFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val color = tileColor(folder.category)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(color)
            .clickable(enabled = folder.count > 0) { onClick() }
    ) {
        // Decorative blob
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = Color.White.copy(alpha = 0.10f),
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * 0.75f, size.height * 0.65f)
            )
        }

        Column(modifier = Modifier.padding(spacing.cardPadding + 4.dp)) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        iconForCategory(folder.category),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                folder.category.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                if (folder.count == 0) "0 docs" else "${folder.count} docs",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun FolderTileSmall(
    folder: VaultFolder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val color = tileColor(folder.category)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color)
            .clickable(enabled = folder.count > 0) { onClick() }
            .padding(spacing.md)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Icon(
                iconForCategory(folder.category),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                folder.category.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (folder.count == 1) "1 doc" else "${folder.count} docs",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
    }
}

@Composable
private fun RecentDocumentRow(
    document: com.thalock.app.data.model.Document,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(spacing.cardGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    document.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "${DocumentCategory.forDocumentType(document.documentType).displayName} · ${
                        relativeLabel(document.createdAt)
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun relativeLabel(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val day = 24L * 60 * 60 * 1000
    val diff = now - timestamp
    return when {
        diff < day -> "Today"
        diff < 2 * day -> "Yesterday"
        diff < 7 * day -> "${diff / day} days ago"
        else -> "${diff / (7 * day)}w ago"
    }
}

@Composable
private fun tileColor(category: DocumentCategory): Color = when (category) {
    DocumentCategory.IDENTITY -> CategoryColors.IdentityTile
    DocumentCategory.FINANCIAL -> CategoryColors.FinancialTile
    DocumentCategory.INSURANCE -> CategoryColors.InsuranceTile
}

@Composable
private fun iconForCategory(category: DocumentCategory): ImageVector = when (category) {
    DocumentCategory.IDENTITY -> Icons.Outlined.Badge
    DocumentCategory.FINANCIAL -> Icons.Outlined.AccountBalance
    DocumentCategory.INSURANCE -> Icons.Outlined.Shield
}

@Composable
private fun EmptyVaultState() {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
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
                        Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(spacing.md))
            Text(
                "Your vault is empty",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(spacing.xs))
            Text(
                "Tap + to add your first document",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    val spacing = LocalSpacing.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(spacing.xl),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.SearchOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(spacing.md))
            Text(
                "No results for \"$query\"",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

