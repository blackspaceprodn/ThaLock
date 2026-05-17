package com.thalock.app.ui.screens.add

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import com.thalock.app.data.model.Country
import com.thalock.app.data.model.DocumentCategory
import com.thalock.app.data.model.DocumentTemplate
import com.thalock.app.data.model.DocumentType
import com.thalock.app.ui.theme.CategoryColors
import com.thalock.app.ui.theme.LocalSpacing
import com.thalock.app.ui.theme.ThaLockPrimary
import com.thalock.app.ui.theme.ThaLockPrimarySoft

// =============================================================================
// SCREEN 1: Document Category / Type Selection
// =============================================================================

@Composable
fun DocumentTypeSelectionScreen(
    onTypeSelected: (DocumentType) -> Unit,
    onBack: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<DocumentCategory?>(null) }
    val spacing = LocalSpacing.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.screenHorizontal, vertical = spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "ThaLock.",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold
            )
        }

        Column(modifier = Modifier.padding(horizontal = spacing.screenHorizontal)) {
            val current = selectedCategory
            if (current == null) {
                CategoryPicker(
                    onCategoryClick = { category ->
                        val types = DocumentCategory.typesIn(category)
                        if (types.size == 1) {
                            onTypeSelected(types.first())
                        } else {
                            selectedCategory = category
                        }
                    }
                )
            } else {
                SubTypePicker(
                    category = current,
                    onTypeClick = { onTypeSelected(it) },
                    onBack = { selectedCategory = null }
                )
            }

            Spacer(modifier = Modifier.height(spacing.xxl))
        }
    }
}

@Composable
private fun CategoryPicker(
    onCategoryClick: (DocumentCategory) -> Unit
) {
    val spacing = LocalSpacing.current

    Text(
        "Choose a category",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(spacing.lg))

    DocumentCategory.entries.forEach { category ->
        ColoredCategoryCard(
            category = category,
            onClick = { onCategoryClick(category) }
        )
        Spacer(modifier = Modifier.height(spacing.cardGap))
    }
}

@Composable
private fun ColoredCategoryCard(
    category: DocumentCategory,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    val bg = when (category) {
        DocumentCategory.IDENTITY -> CategoryColors.IdentityTile
        DocumentCategory.FINANCIAL -> CategoryColors.FinancialTile
        DocumentCategory.INSURANCE -> CategoryColors.InsuranceTile
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(spacing.cardPadding + 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.22f),
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        iconForCategory(category),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(spacing.cardGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    category.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    category.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = Color.White
            )
        }
    }
}

@Composable
private fun SubTypePicker(
    category: DocumentCategory,
    onTypeClick: (DocumentType) -> Unit,
    onBack: () -> Unit
) {
    val spacing = LocalSpacing.current

    Row(
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
        Spacer(modifier = Modifier.width(spacing.sm))
        Text(
            category.displayName,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
    }
    Spacer(modifier = Modifier.height(spacing.xs))
    Text(
        "Choose the specific kind of ${category.displayName.lowercase()} document.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Spacer(modifier = Modifier.height(spacing.lg))

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.5.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            val types = DocumentCategory.typesIn(category)
            types.forEachIndexed { index, type ->
                DocumentTypeRow(
                    type = type,
                    onClick = { onTypeClick(type) }
                )
                if (index < types.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = spacing.md),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

@Composable
private fun iconForCategory(category: DocumentCategory): ImageVector = when (category) {
    DocumentCategory.IDENTITY -> Icons.Outlined.Badge
    DocumentCategory.FINANCIAL -> Icons.Outlined.AccountBalance
    DocumentCategory.INSURANCE -> Icons.Outlined.Shield
}

@Composable
private fun DocumentTypeRow(
    type: DocumentType,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            type.displayName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = "Select ${type.displayName}",
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

// =============================================================================
// SCREEN 2: Document Form
// =============================================================================

@Composable
fun AddDocumentFormScreen(
    documentType: DocumentType,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddDocumentViewModel = viewModel()
) {
    val fields by viewModel.fields.collectAsState()
    val title by viewModel.title.collectAsState()
    val selectedCountry by viewModel.selectedCountry.collectAsState()
    val isProcessingOcr by viewModel.isProcessingOcr.collectAsState()
    val ocrError by viewModel.ocrError.collectAsState()
    val pendingOcrReview by viewModel.pendingOcrReview.collectAsState()
    val spacing = LocalSpacing.current

    var showAddFieldDialog by remember { mutableStateOf(false) }
    // Whether the "Scan" button has expanded into its Camera / Gallery
    // chooser dialog.
    var showScanChooser by remember { mutableStateOf(false) }

    val context = LocalContext.current

    // Gallery path for the Scan button: user picks an existing image to OCR.
    // Scoped to image MIMEs via GetContent so the system surfaces the photos
    // UI rather than the generic file manager.
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val resolver = context.contentResolver
            val name = queryDisplayName(context, it)
            viewModel.processOcr(
                uri = it,
                displayName = name,
                mimeType = resolver.getType(it),
                source = AttachmentSource.UPLOAD
            )
        }
    }

    // Upload path: user picks ANY file (PDF, doc, image, etc.) from the
    // system file browser. OpenDocument surfaces the DocumentsUI picker so
    // the user sees their real Downloads / Drive / internal storage — not
    // just the photo gallery. Non-image uploads skip OCR in the ViewModel
    // and are simply filed in the Files tab on save.
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            val resolver = context.contentResolver
            // Take a persistable read grant so the URI stays readable long
            // enough for us to copy the bytes into encrypted storage. The
            // takePersistableUriPermission call is safe to no-op if the
            // provider refuses; we catch and ignore.
            runCatching {
                resolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val name = queryDisplayName(context, it)
            viewModel.processOcr(
                uri = it,
                displayName = name,
                mimeType = resolver.getType(it),
                source = AttachmentSource.UPLOAD
            )
        }
    }

    // Camera-capture path: we stage a FileProvider URI into cache/captures
    // ahead of time and hand it to TakePicture. The contract writes the
    // JPEG there, then we feed that same URI through the OCR+stage flow.
    // Cache file is deleted once the ViewModel has re-encrypted the bytes.
    var pendingCameraFile by remember { mutableStateOf<File?>(null) }
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        val capturedFile = pendingCameraFile
        val capturedUri = pendingCameraUri
        pendingCameraFile = null
        pendingCameraUri = null
        if (success && capturedUri != null) {
            viewModel.processOcr(
                uri = capturedUri,
                displayName = capturedFile?.name,
                mimeType = "image/jpeg",
                source = AttachmentSource.CAMERA
            )
        } else {
            // User cancelled — clean up the empty scratch file.
            capturedFile?.delete()
        }
    }

    // Because we declare android.permission.CAMERA in the manifest, the
    // TakePicture intent will be denied silently unless the user has granted
    // the runtime permission. Request it on demand; on grant, immediately
    // launch the camera with the prepared URI.
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted: Boolean ->
        val uri = pendingCameraUri
        if (granted && uri != null) {
            cameraLauncher.launch(uri)
        } else if (!granted) {
            // User denied — discard the scratch file we prepared.
            pendingCameraFile?.delete()
            pendingCameraFile = null
            pendingCameraUri = null
        }
    }

    // Shared entry point for the camera flow — reused by the Scan chooser's
    // "Camera" option. Prepares a FileProvider URI, then either launches the
    // camera directly (permission already granted) or routes through the
    // runtime-permission request first.
    val launchCamera: () -> Unit = {
        val dir = File(context.cacheDir, "captures").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        pendingCameraFile = file
        pendingCameraUri = uri
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.CAMERA
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }

    // Initialize VM with chosen type
    LaunchedEffect(documentType) {
        viewModel.selectDocumentType(documentType)
    }

    val templateFieldCount = DocumentTemplate.fieldsFor(documentType).size

    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable content
        Column(
            modifier = Modifier
                .weight(1f)
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
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(spacing.touchTarget)
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Go back",
                        tint = ThaLockPrimary
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    "ThaLock.",
                    style = MaterialTheme.typography.titleLarge,
                    color = ThaLockPrimary,
                    fontWeight = FontWeight.ExtraBold
                )
            }

                // Header
                Text(
                    "Enter Details",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(spacing.xs))
                Text(
                    "Add common details below. Use the buttons to add or remove fields as needed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(spacing.lg))

                // Document type card
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.cardPadding),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon block
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ThaLockPrimary,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                val category = DocumentCategory.forDocumentType(documentType)
                                Icon(
                                    when (category) {
                                        DocumentCategory.FINANCIAL -> Icons.Outlined.AccountBalance
                                        DocumentCategory.INSURANCE -> Icons.Outlined.Shield
                                        DocumentCategory.IDENTITY -> Icons.Outlined.Badge
                                    },
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(spacing.cardGap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "DOCUMENT TYPE",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                letterSpacing = 1.sp
                            )
                            Text(
                                documentType.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.lg))

                // Optional country selector
                FieldLabel("Country (optional)")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(spacing.sm)) {
                    item {
                        FilterChip(
                            selected = selectedCountry == null,
                            onClick = { viewModel.selectCountry(null) },
                            label = {
                                Text(
                                    "None",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = ThaLockPrimary,
                                selectedLabelColor = Color.White
                            ),
                            border = null,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.height(36.dp)
                        )
                    }
                    items(Country.entries.toList()) { country ->
                        FilterChip(
                            selected = selectedCountry == country,
                            onClick = { viewModel.selectCountry(country) },
                            label = {
                                Text(
                                    country.shortLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedContainerColor = ThaLockPrimary,
                                selectedLabelColor = Color.White
                            ),
                            border = null,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.height(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(spacing.lg))

                // Scan / upload options — two buttons side by side.
                // Camera launches TakePicture with a FileProvider-backed temp
                // URI; Upload pulls from gallery/files via GetContent. Both
                // run OCR and, on save, are filed into the Files tab as an
                // UploadedFile linked to the saved Document.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalButton(
                        onClick = { showScanChooser = true },
                        enabled = !isProcessingOcr,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(
                            "Scan",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FilledTonalButton(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        enabled = !isProcessingOcr,
                        shape = RoundedCornerShape(999.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            Icons.Outlined.UploadFile,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(spacing.sm))
                        Text(
                            "Upload",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                AnimatedVisibility(visible = isProcessingOcr) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = spacing.md)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(spacing.cardGap))
                        Text(
                            "Extracting details...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ocrError?.let { error ->
                    Spacer(modifier = Modifier.height(spacing.sm))
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                AnimatedVisibility(
                    visible = pendingOcrReview,
                    enter = fadeIn() + expandVertically()
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = spacing.md)
                    ) {
                        Column(modifier = Modifier.padding(spacing.cardPadding)) {
                            Text(
                                "Review Scanned Details",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.height(spacing.xs))
                            Text(
                                "Verify the extracted details below. Edit any incorrect values before confirming.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer.copy(
                                    alpha = 0.7f
                                )
                            )
                            Spacer(modifier = Modifier.height(spacing.md))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(spacing.cardGap),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.discardOcrReview() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Discard")
                                }
                                Button(
                                    onClick = { viewModel.confirmOcrReview() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = ThaLockPrimary
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Looks Good")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.lg))

                // Title
                VaultField(
                    label = "Document Title",
                    value = title,
                    onValueChange = { viewModel.setTitle(it) },
                    placeholder = "e.g. My ${documentType.displayName}"
                )

                Spacer(modifier = Modifier.height(spacing.fieldGap))

                // Document fields
                fields.forEachIndexed { index, field ->
                    val isUserAdded = index >= templateFieldCount

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        VaultField(
                            label = field.label,
                            value = field.value,
                            onValueChange = { viewModel.updateField(index, it) },
                            placeholder = getPlaceholder(field.label),
                            trailingIcon = trailingIconFor(field.label, field.isSensitive),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.removeField(index) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                Icons.Outlined.RemoveCircle,
                                contentDescription = "Remove ${field.label}",
                                tint = if (isUserAdded) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(spacing.fieldGap))
                }

                // Add field
                OutlinedButton(
                    onClick = { showAddFieldDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(spacing.sm))
                    Text("Add Field")
                }

                Spacer(modifier = Modifier.height(spacing.md))

                // AES-256 encryption note
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(spacing.cardPadding),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(spacing.cardGap)
                    ) {
                        Icon(
                            Icons.Outlined.Shield,
                            contentDescription = null,
                            tint = ThaLockPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                "AES-256 Encryption",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = ThaLockPrimary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                "Your data is encrypted locally and stored only on this device. ThaLock has no access to your raw document data.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(spacing.xxl))
        }

        // Save button pinned at bottom
        Surface(
            tonalElevation = 2.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface
        ) {
            Button(
                onClick = { viewModel.saveDocument { onSaved() } },
                enabled = !pendingOcrReview,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ThaLockPrimarySoft,
                    contentColor = Color(0xFF1A1A24),
                    disabledContainerColor = ThaLockPrimarySoft.copy(alpha = 0.4f),
                    disabledContentColor = Color(0xFF1A1A24).copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = spacing.screenHorizontal,
                        vertical = spacing.md
                    )
                    .height(56.dp)
            ) {
                Icon(
                    Icons.Outlined.Lock,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(spacing.sm))
                Text(
                    "Save to Vault",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    if (showAddFieldDialog) {
        AddCustomFieldDialog(
            onDismiss = { showAddFieldDialog = false },
            onAdd = { label, isSensitive ->
                viewModel.addCustomField(label, isSensitive)
                showAddFieldDialog = false
            }
        )
    }

    if (showScanChooser) {
        ScanSourceChooserDialog(
            onDismiss = { showScanChooser = false },
            onCamera = {
                showScanChooser = false
                launchCamera()
            },
            onGallery = {
                showScanChooser = false
                galleryLauncher.launch("image/*")
            }
        )
    }
}

/**
 * Chooser shown when the user taps "Scan" — lets them grab an image from
 * the camera or pick an existing photo from the gallery. Both paths end up
 * running OCR and staging the bytes as an attachment; only the source
 * differs. The Upload button bypasses this dialog entirely and goes to the
 * SAF file picker for arbitrary file types.
 */
@Composable
private fun ScanSourceChooserDialog(
    onDismiss: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit
) {
    val spacing = LocalSpacing.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scan document") },
        text = {
            Column {
                Text(
                    "Choose where to pull the image from. We'll run text recognition and autofill the fields.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(spacing.md))
                ScanSourceOption(
                    icon = Icons.Outlined.PhotoCamera,
                    title = "Camera",
                    subtitle = "Take a new photo",
                    onClick = onCamera
                )
                Spacer(modifier = Modifier.height(spacing.sm))
                ScanSourceOption(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Gallery",
                    subtitle = "Pick an existing photo",
                    onClick = onGallery
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ScanSourceOption(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val spacing = LocalSpacing.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(spacing.cardPadding),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = ThaLockPrimary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(spacing.cardGap))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FieldLabel(label: String) {
    val spacing = LocalSpacing.current
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(bottom = spacing.xs)
    )
}

/**
 * Filled dark rounded field — label sits stacked above the value inside a single surface,
 * no outline. Matches the form style in the redesign spec.
 */
@Composable
private fun VaultField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    trailingIcon: ImageVector? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.4.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(
                        MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        inner()
                    }
                )
            }
            if (trailingIcon != null) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    trailingIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

private fun trailingIconFor(label: String, isSensitive: Boolean): ImageVector? {
    if (isSensitive) return Icons.Outlined.VisibilityOff
    val lower = label.lowercase()
    return when {
        "name" in lower -> Icons.Outlined.Person
        "address" in lower -> Icons.Outlined.LocationOn
        else -> null
    }
}

@Composable
private fun AddCustomFieldDialog(
    onDismiss: () -> Unit,
    onAdd: (label: String, isSensitive: Boolean) -> Unit
) {
    var fieldLabel by remember { mutableStateOf("") }
    var isSensitive by remember { mutableStateOf(false) }
    val spacing = LocalSpacing.current

    AlertDialog(
        onDismissRequest = onDismiss,
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
                    if (fieldLabel.isNotBlank()) onAdd(fieldLabel, isSensitive)
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Best-effort display-name lookup for a content URI picked through GetContent.
 * Gmail, Google Files, and DocumentsUI all expose OpenableColumns.DISPLAY_NAME
 * in their cursors; if the provider doesn't, we fall back to the URI's last
 * path segment.
 */
private fun queryDisplayName(context: android.content.Context, uri: Uri): String? {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) {
            return cursor.getString(idx)
        }
    }
    return uri.lastPathSegment
}

private fun getPlaceholder(label: String): String {
    val lower = label.lowercase()
    return when {
        "name" in lower -> "As printed on document"
        "number" in lower && "phone" !in lower -> "XXXX - XXXX - XXXX"
        "date" in lower && "birth" in lower -> "mm/dd/yyyy"
        "date" in lower -> "mm/dd/yyyy"
        "expiry" in lower -> "mm/yy"
        "cvv" in lower -> "•••"
        "address" in lower -> "Enter complete address as shown"
        "phone" in lower || "mobile" in lower -> "+91 XXXXX XXXXX"
        "email" in lower -> "email@example.com"
        else -> "Enter ${label.lowercase()}"
    }
}
