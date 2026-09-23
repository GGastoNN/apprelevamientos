@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.illu.relevametal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.illu.relevametal.R
import com.illu.relevametal.annotation.AnnotationCodec
import com.illu.relevametal.annotation.AnnotationTool
import com.illu.relevametal.annotation.MarkupAnnotation
import com.illu.relevametal.annotation.MeasurementAnnotation
import com.illu.relevametal.branding.BrandingSettings
import com.illu.relevametal.data.EvidenceEntity
import com.illu.relevametal.data.EventEntity
import com.illu.relevametal.data.OpeningEntity
import com.illu.relevametal.data.ProjectEntity
import com.illu.relevametal.data.ProjectReferencePhotoEntity
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

@Composable
fun ProjectsScreen(
    vm: AppViewModel,
    onOpen: (Long) -> Unit,
    onShareData: (java.io.File) -> Unit
) {
    val projects by vm.projects.collectAsState()
    var query by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("TODAS") }
    var newProject by remember { mutableStateOf(false) }
    var showBranding by remember { mutableStateOf(false) }
    var showDataTools by remember { mutableStateOf(false) }
    var dataBusy by remember { mutableStateOf(false) }
    var dataError by remember { mutableStateOf<String?>(null) }
    var dataMessage by remember { mutableStateOf<String?>(null) }

    val importDataLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            dataBusy = true
            showDataTools = false
            vm.importDataArchive(uri) { result ->
                dataBusy = false
                result.onSuccess { imported ->
                    val totalPhotos = imported.evidencePhotos + imported.referencePhotos
                    dataMessage = "Importación completa: ${imported.projects} obra(s), ${imported.spaces} espacio(s), ${imported.openings} vano(s) y $totalPhotos foto(s)."
                }.onFailure {
                    dataError = it.message ?: "No se pudo importar el archivo"
                }
            }
        }
    }

    val filtered = remember(projects, query, status) {
        projects.filter { project ->
            val matchesStatus = status == "TODAS" || project.status == status
            val q = query.trim()
            val matchesQuery = q.isBlank() || listOf(
                project.name,
                project.client,
                project.address,
                project.responsible
            ).any { it.contains(q, ignoreCase = true) }
            matchesStatus && matchesQuery
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(42.dp),
                            shape = RoundedCornerShape(11.dp)
                        ) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(R.drawable.app_logo),
                                contentDescription = "Logo de la aplicación",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        Column {
                            Text("Grupo IDEA", fontWeight = FontWeight.Bold)
                            Text(
                                "Relevamientos",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                },
                actions = {
                    TextButton(onClick = { showDataTools = true }) { Text("Datos") }
                    TextButton(onClick = { showBranding = true }) { Text("Marca") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { newProject = true },
                text = { Text("Nueva obra") },
                icon = { Text("+") }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Buscar obra, cliente o dirección") }
                )
            }

            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("TODAS", "EN_CURSO", "PAUSADA", "FINALIZADA").forEach { value ->
                        FilterChip(
                            selected = status == value,
                            onClick = { status = value },
                            label = { Text(statusLabel(value)) }
                        )
                    }
                }
            }

            if (filtered.isEmpty()) {
                item {
                    EmptyState(
                        title = if (projects.isEmpty()) "Todavía no hay obras" else "Sin resultados",
                        text = if (projects.isEmpty()) {
                            "Creá una obra y empezá a registrar espacios, vanos, fotos y cotas."
                        } else {
                            "Probá cambiando la búsqueda o el filtro."
                        }
                    )
                }
            } else {
                items(items = filtered, key = { it.id }) { project ->
                    ProjectCard(project = project, onClick = { onOpen(project.id) })
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (newProject) {
        NewProjectDialog(
            onDismiss = { newProject = false },
            onSave = { name, client, address, responsible, notes ->
                vm.addProject(name, client, address, responsible, notes) { id ->
                    newProject = false
                    onOpen(id)
                }
            }
        )
    }

    if (showBranding) {
        BrandingDialog(vm = vm, onDismiss = { showBranding = false })
    }

    if (showDataTools) {
        DataTransferDialog(
            projectCount = projects.size,
            busy = dataBusy,
            onDismiss = { if (!dataBusy) showDataTools = false },
            onExportAll = {
                if (projects.isEmpty()) return@DataTransferDialog
                dataBusy = true
                vm.exportDataArchive(projects.map { it.id }) { result ->
                    dataBusy = false
                    result.onSuccess { file ->
                        showDataTools = false
                        onShareData(file)
                    }.onFailure {
                        showDataTools = false
                        dataError = it.message ?: "No se pudo crear el archivo de transferencia"
                    }
                }
            },
            onImport = {
                importDataLauncher.launch(
                    arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                )
            }
        )
    }

    if (dataBusy && !showDataTools) {
        AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            title = { Text("Importando datos") },
            text = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.5.dp)
                    Text("Copiando obras, fotografías y cotas…")
                }
            }
        )
    }

    dataError?.let { message ->
        AlertDialog(
            onDismissRequest = { dataError = null },
            confirmButton = { TextButton(onClick = { dataError = null }) { Text("Aceptar") } },
            title = { Text("Transferencia de datos") },
            text = { Text(message) }
        )
    }

    dataMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dataMessage = null },
            confirmButton = { TextButton(onClick = { dataMessage = null }) { Text("Aceptar") } },
            title = { Text("Datos importados") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun DataTransferDialog(
    projectCount: Int,
    busy: Boolean,
    onDismiss: () -> Unit,
    onExportAll: () -> Unit,
    onImport: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Cerrar") }
        },
        title = { Text("Transferencia de datos") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Podés mover las obras completas a otro dispositivo con RelevaMetal. " +
                        "El archivo .gidea incluye espacios, vanos, medidas, controles, bitácora, fotos originales, cotas y anotaciones."
                )
                if (busy) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Procesando archivo…")
                    }
                }
                Button(
                    onClick = onExportAll,
                    enabled = !busy && projectCount > 0,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (projectCount == 1) "Exportar la obra" else "Exportar todas las obras ($projectCount)")
                }
                OutlinedButton(
                    onClick = onImport,
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Importar archivo de datos")
                }
                Text(
                    "Al importar, las obras se agregan como nuevas y no reemplazan las que ya existen en el dispositivo.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun ProjectCard(project: ProjectEntity, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (project.client.isNotBlank()) {
                        Text(project.client, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                StatusBadge(project.status)
            }
            if (project.address.isNotBlank()) {
                Text(
                    project.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Actualizada ${dateFormat.format(Date(project.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ProjectScreen(
    vm: AppViewModel,
    projectId: Long,
    onBack: () -> Unit,
    onOpenSpace: (Long) -> Unit,
    onSharePdf: (java.io.File) -> Unit,
    onShareData: (java.io.File) -> Unit
) {
    val spaces by vm.spaces(projectId).collectAsState(initial = emptyList())
    val events by vm.events(projectId).collectAsState(initial = emptyList())
    val referencePhotos by vm.projectReferencePhotos(projectId).collectAsState(initial = emptyList())
    val stats by vm.projectStats(projectId).collectAsState(initial = ProjectStats())
    var project by remember(projectId) { mutableStateOf<ProjectEntity?>(null) }
    var tab by remember { mutableIntStateOf(0) }
    var showSpaceDialog by remember { mutableStateOf(false) }
    var showEventDialog by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var loadingPdfChoices by remember { mutableStateOf(false) }
    var pdfChoices by remember { mutableStateOf<List<PdfOpeningPhotoChoice>>(emptyList()) }
    var showPdfExportDialog by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var showProjectDataDialog by remember { mutableStateOf(false) }
    var dataExporting by remember { mutableStateOf(false) }
    var dataExportError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(projectId) { project = vm.project(projectId) }
    val p = project

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(p?.name ?: "Obra", maxLines = 1, overflow = TextOverflow.Ellipsis)
                        p?.client?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    TextButton(
                        enabled = !dataExporting,
                        onClick = { showProjectDataDialog = true }
                    ) {
                        Text(if (dataExporting) "Exportando…" else "Datos")
                    }
                    TextButton(
                        enabled = !exporting && !loadingPdfChoices && !dataExporting,
                        onClick = {
                            loadingPdfChoices = true
                            vm.loadPdfPhotoChoices(projectId) { result ->
                                loadingPdfChoices = false
                                result.onSuccess { choices ->
                                    pdfChoices = choices
                                    showPdfExportDialog = true
                                }.onFailure {
                                    exportError = it.message ?: "No se pudo preparar el PDF"
                                }
                            }
                        }
                    ) {
                        Text(
                            when {
                                exporting -> "Generando…"
                                loadingPdfChoices -> "Preparando…"
                                else -> "PDF"
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { if (tab == 0) showSpaceDialog = true else showEventDialog = true },
                text = { Text(if (tab == 0) "Nuevo espacio" else "Nueva nota") },
                icon = { Text("+") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            ProjectStatsStrip(stats)
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Espacios") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bitácora") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Obra") })
            }

            when (tab) {
                0 -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (spaces.isEmpty()) {
                            item {
                                EmptyState(
                                    "Sin espacios cargados",
                                    "Dividí la obra por planta, ambiente, fachada o sector para trabajar más rápido."
                                )
                            }
                        }
                        items(items = spaces, key = { it.id }) { space ->
                            ElevatedCard(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenSpace(space.id) }
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(space.name, fontWeight = FontWeight.SemiBold)
                                    val context = listOf(space.level, space.sector)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · ")
                                    if (context.isNotBlank()) {
                                        Text(context, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (space.notes.isNotBlank()) {
                                        Text(
                                            space.notes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }

                1 -> Timeline(events)
                else -> ProjectInfoEditor(
                    vm = vm,
                    project = p,
                    referencePhotos = referencePhotos,
                    onSave = {
                        project = it
                        vm.updateProject(it)
                    }
                )
            }
        }
    }

    if (showSpaceDialog) {
        NewSpaceDialog(
            onDismiss = { showSpaceDialog = false },
            onSave = { name, level, sector, notes ->
                vm.addSpace(projectId, name, level, sector, notes) { id ->
                    showSpaceDialog = false
                    onOpenSpace(id)
                }
            }
        )
    }

    if (showEventDialog) {
        NewEventDialog(
            onDismiss = { showEventDialog = false },
            onSave = { title, detail, severity ->
                vm.addEvent(projectId, title, detail, severity)
                showEventDialog = false
            }
        )
    }

    if (showPdfExportDialog) {
        PdfExportDialog(
            choices = pdfChoices,
            onDismiss = { showPdfExportDialog = false },
            onGenerate = { selection ->
                showPdfExportDialog = false
                exporting = true
                vm.exportProject(projectId, selection) { result ->
                    exporting = false
                    result.onSuccess(onSharePdf)
                        .onFailure { exportError = it.message ?: "No se pudo generar el PDF" }
                }
            }
        )
    }

    if (showProjectDataDialog) {
        AlertDialog(
            onDismissRequest = { if (!dataExporting) showProjectDataDialog = false },
            confirmButton = {
                TextButton(
                    enabled = !dataExporting,
                    onClick = {
                        dataExporting = true
                        vm.exportDataArchive(listOf(projectId)) { result ->
                            dataExporting = false
                            result.onSuccess { file ->
                                showProjectDataDialog = false
                                onShareData(file)
                            }.onFailure {
                                showProjectDataDialog = false
                                dataExportError = it.message ?: "No se pudo exportar la obra"
                            }
                        }
                    }
                ) { Text(if (dataExporting) "Exportando…" else "Exportar obra") }
            },
            dismissButton = {
                TextButton(
                    enabled = !dataExporting,
                    onClick = { showProjectDataDialog = false }
                ) { Text("Cancelar") }
            },
            title = { Text("Exportar datos de la obra") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Se generará un archivo .gidea portable con toda la obra: espacios, vanos, medidas, controles, bitácora, " +
                            "fotos generales, fotos de cada vano y todas sus cotas y anotaciones."
                    )
                    Text(
                        "Las fotos se guardan una sola vez junto con sus datos técnicos para evitar duplicar peso innecesariamente.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }

    dataExportError?.let { message ->
        AlertDialog(
            onDismissRequest = { dataExportError = null },
            confirmButton = { TextButton(onClick = { dataExportError = null }) { Text("Aceptar") } },
            title = { Text("No se pudo exportar la obra") },
            text = { Text(message) }
        )
    }

    exportError?.let { message ->
        AlertDialog(
            onDismissRequest = { exportError = null },
            confirmButton = {
                TextButton(onClick = { exportError = null }) { Text("Aceptar") }
            },
            title = { Text("No se pudo generar el informe") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun PdfExportDialog(
    choices: List<PdfOpeningPhotoChoice>,
    onDismiss: () -> Unit,
    onGenerate: (Map<Long, Long?>) -> Unit
) {
    var selections by remember(choices) {
        mutableStateOf<Map<Long, Long?>>(choices.associate { it.openingId to it.defaultEvidenceId })
    }
    var picking by remember { mutableStateOf<PdfOpeningPhotoChoice?>(null) }

    val currentPicker = picking
    if (currentPicker != null) {
        PdfPhotoPickerDialog(
            choice = currentPicker,
            selectedEvidenceId = selections[currentPicker.openingId],
            onDismiss = { picking = null },
            onSelect = { evidenceId ->
                selections = selections.toMutableMap().apply {
                    put(currentPicker.openingId, evidenceId)
                }
                picking = null
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onGenerate(selections) }) { Text("Generar PDF") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        title = { Text("Preparar PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Para reducir hojas y peso, el informe incluye como máximo una foto por vano. " +
                        "Por defecto queda seleccionada la última foto tomada; podés cambiarla u omitirla."
                )
                if (choices.isEmpty()) {
                    Text(
                        "La obra todavía no tiene vanos. Se generará la portada y la información disponible.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 480.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(items = choices, key = { it.openingId }) { choice ->
                            PdfOpeningPhotoRow(
                                choice = choice,
                                selectedEvidenceId = selections[choice.openingId],
                                onChange = { if (choice.photos.isNotEmpty()) picking = choice }
                            )
                        }
                    }
                }
                Text(
                    "El PDF usa una ficha compacta por vano con medidas y foto en la misma hoja.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun PdfOpeningPhotoRow(
    choice: PdfOpeningPhotoChoice,
    selectedEvidenceId: Long?,
    onChange: () -> Unit
) {
    val selected = choice.photos.firstOrNull { it.id == selectedEvidenceId }
    val bitmap by rememberPhotoBitmap(
        path = selected?.filePath.orEmpty(),
        maxSide = 260,
        rotationDegrees = selected?.rotationDegrees ?: 0
    )

    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                Modifier
                    .size(width = 78.dp, height = 68.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap!!,
                        contentDescription = "Foto elegida para ${choice.openingCode}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Text(
                        if (selected == null) "Sin foto" else "—",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(choice.openingCode, fontWeight = FontWeight.SemiBold)
                Text(choice.spaceName, style = MaterialTheme.typography.bodySmall)
                if (selected != null) {
                    Text(
                        "${phaseLabel(selected.phase)} · ${dateFormat.format(Date(selected.createdAt))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (selected.id == choice.defaultEvidenceId) {
                        Text(
                            "Última foto · selección predeterminada",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                } else {
                    Text(
                        if (choice.photos.isEmpty()) "Este vano no tiene fotografías" else "No se incluirá fotografía",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            TextButton(
                onClick = onChange,
                enabled = choice.photos.isNotEmpty()
            ) { Text("Cambiar") }
        }
    }
}

@Composable
private fun PdfPhotoPickerDialog(
    choice: PdfOpeningPhotoChoice,
    selectedEvidenceId: Long?,
    onDismiss: () -> Unit,
    onSelect: (Long?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        title = { Text("Foto para ${choice.openingCode}") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(null) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedEvidenceId == null,
                                onClick = { onSelect(null) }
                            )
                            Column {
                                Text("No incluir foto", fontWeight = FontWeight.SemiBold)
                                Text(
                                    "Reduce todavía más el tamaño del informe.",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                }

                items(items = choice.photos, key = { it.id }) { photo ->
                    PdfPhotoPickerRow(
                        photo = photo,
                        isSelected = photo.id == selectedEvidenceId,
                        isLatest = photo.id == choice.defaultEvidenceId,
                        onClick = { onSelect(photo.id) }
                    )
                }
            }
        }
    )
}

@Composable
private fun PdfPhotoPickerRow(
    photo: EvidenceEntity,
    isSelected: Boolean,
    isLatest: Boolean,
    onClick: () -> Unit
) {
    val bitmap by rememberPhotoBitmap(
        path = photo.filePath,
        maxSide = 320,
        rotationDegrees = photo.rotationDegrees
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (isSelected) 3.dp else 0.dp
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RadioButton(selected = isSelected, onClick = onClick)
            Box(
                Modifier
                    .size(width = 92.dp, height = 72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it,
                        contentDescription = "Fotografía del vano",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isLatest) "Última foto" else phaseLabel(photo.phase),
                    fontWeight = if (isLatest) FontWeight.SemiBold else FontWeight.Normal
                )
                Text(
                    "${phaseLabel(photo.phase)} · ${dateFormat.format(Date(photo.createdAt))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (photo.caption.isNotBlank()) {
                    Text(
                        photo.caption,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ProjectStatsStrip(stats: ProjectStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricCard("Espacios", stats.spaces.toString())
        MetricCard("Vanos", stats.openings.toString())
        MetricCard("Pendientes", stats.pending.toString())
        MetricCard("Fotos", stats.photos.toString())
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun SpaceScreen(
    vm: AppViewModel,
    projectId: Long,
    spaceId: Long,
    onBack: () -> Unit,
    onOpenOpening: (Long) -> Unit
) {
    val openings by vm.openings(spaceId).collectAsState(initial = emptyList())
    var spaceName by remember { mutableStateOf("Vanos") }
    var showDialog by remember { mutableStateOf(false) }
    var suggestedCode by remember { mutableStateOf("V01") }

    LaunchedEffect(spaceId) { spaceName = vm.space(spaceId)?.name ?: "Vanos" }
    LaunchedEffect(showDialog, openings.size) {
        if (showDialog) suggestedCode = vm.nextOpeningCode(spaceId)
    }

    val completed = openings.count { it.status == "RELEVADO" || it.status == "APROBADO" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(spaceName)
                        Text(
                            "$completed de ${openings.size} relevados",
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("←") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                text = { Text("Nuevo vano") },
                icon = { Text("+") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            if (openings.isEmpty()) {
                item {
                    EmptyState(
                        "Sin vanos",
                        "El código se propone automáticamente para cargar rápido durante el recorrido."
                    )
                }
            }
            items(items = openings, key = { it.id }) { opening ->
                OpeningListCard(opening) { onOpenOpening(opening.id) }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showDialog) {
        NewOpeningDialog(
            initialCode = suggestedCode,
            onDismiss = { showDialog = false },
            onSave = { code, type ->
                vm.addOpening(projectId, spaceId, code, type) { id ->
                    showDialog = false
                    onOpenOpening(id)
                }
            }
        )
    }
}

@Composable
private fun OpeningListCard(opening: OpeningEntity, onClick: () -> Unit) {
    ElevatedCard(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "${opening.code} · ${opening.type}",
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${opening.widthMm ?: "—"} × ${opening.heightMm ?: "—"} mm",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (opening.notes.isNotBlank()) {
                    Text(
                        opening.notes,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            StatusBadge(opening.status)
        }
    }
}

@Composable
fun OpeningScreen(
    vm: AppViewModel,
    projectId: Long,
    openingId: Long,
    onBack: () -> Unit,
    onCamera: () -> Unit,
    onEditEvidence: (Long) -> Unit,
    onDuplicated: (Long) -> Unit
) {
    var opening by remember(openingId) { mutableStateOf<OpeningEntity?>(null) }
    val evidence by vm.evidence(openingId).collectAsState(initial = emptyList())
    val branding by vm.branding.collectAsState()
    val brandLogo by rememberPhotoBitmap(branding.logoPath, maxSide = 220)

    LaunchedEffect(openingId) { opening = vm.opening(openingId) }
    val o = opening ?: return

    var code by remember(o.id, o.updatedAt) { mutableStateOf(o.code) }
    var type by remember(o.id, o.updatedAt) { mutableStateOf(o.type) }
    var width by remember(o.id, o.updatedAt) { mutableStateOf(o.widthMm?.toString().orEmpty()) }
    var height by remember(o.id, o.updatedAt) { mutableStateOf(o.heightMm?.toString().orEmpty()) }
    var sill by remember(o.id, o.updatedAt) { mutableStateOf(o.sillMm?.toString().orEmpty()) }
    var d1 by remember(o.id, o.updatedAt) { mutableStateOf(o.diagonal1Mm?.toString().orEmpty()) }
    var d2 by remember(o.id, o.updatedAt) { mutableStateOf(o.diagonal2Mm?.toString().orEmpty()) }
    var wall by remember(o.id, o.updatedAt) { mutableStateOf(o.wallThicknessMm?.toString().orEmpty()) }
    var depth by remember(o.id, o.updatedAt) { mutableStateOf(o.depthMm?.toString().orEmpty()) }
    var left by remember(o.id, o.updatedAt) { mutableStateOf(o.clearanceLeftMm?.toString().orEmpty()) }
    var right by remember(o.id, o.updatedAt) { mutableStateOf(o.clearanceRightMm?.toString().orEmpty()) }
    var top by remember(o.id, o.updatedAt) { mutableStateOf(o.clearanceTopMm?.toString().orEmpty()) }
    var bottom by remember(o.id, o.updatedAt) { mutableStateOf(o.clearanceBottomMm?.toString().orEmpty()) }
    var status by remember(o.id, o.updatedAt) { mutableStateOf(o.status) }
    var plumb by remember(o.id, o.updatedAt) { mutableStateOf(o.plumbState) }
    var level by remember(o.id, o.updatedAt) { mutableStateOf(o.levelState) }
    var square by remember(o.id, o.updatedAt) { mutableStateOf(o.squareState) }
    var floor by remember(o.id, o.updatedAt) { mutableStateOf(o.floorState) }
    var plaster by remember(o.id, o.updatedAt) { mutableStateOf(o.plasterState) }
    var premarco by remember(o.id, o.updatedAt) { mutableStateOf(o.premarcoState) }
    var direction by remember(o.id, o.updatedAt) { mutableStateOf(o.openingDirection) }
    var interference by remember(o.id, o.updatedAt) { mutableStateOf(o.interference) }
    var notes by remember(o.id, o.updatedAt) { mutableStateOf(o.notes) }
    var savedFlash by remember { mutableStateOf(false) }
    var showIncidenceDialog by remember { mutableStateOf(false) }

    fun buildOpening(): OpeningEntity = o.copy(
        code = code.trim().uppercase().ifBlank { o.code },
        type = type.trim().uppercase().ifBlank { "VANO" },
        widthMm = width.toIntOrNull(),
        heightMm = height.toIntOrNull(),
        sillMm = sill.toIntOrNull(),
        diagonal1Mm = d1.toIntOrNull(),
        diagonal2Mm = d2.toIntOrNull(),
        wallThicknessMm = wall.toIntOrNull(),
        depthMm = depth.toIntOrNull(),
        clearanceLeftMm = left.toIntOrNull(),
        clearanceRightMm = right.toIntOrNull(),
        clearanceTopMm = top.toIntOrNull(),
        clearanceBottomMm = bottom.toIntOrNull(),
        status = status,
        plumbState = plumb,
        levelState = level,
        squareState = square,
        floorState = floor,
        plasterState = plaster,
        premarcoState = premarco,
        openingDirection = direction.trim(),
        interference = interference.trim(),
        notes = notes.trim()
    )

    val draftSignature = listOf(
        code, type, width, height, sill, d1, d2, wall, depth, left, right, top, bottom,
        status, plumb, level, square, floor, plaster, premarco, direction, interference, notes
    ).joinToString("\u001F")

    LaunchedEffect(draftSignature) {
        delay(900)
        vm.saveOpeningDraft(projectId, buildOpening())
    }

    BackHandler {
        vm.saveOpeningDraft(projectId, buildOpening())
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(code.ifBlank { o.code })
                        Text(type.ifBlank { "VANO" }, style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { TextButton(onClick = {
                    val updated = buildOpening()
                    vm.saveOpeningDraft(projectId, updated)
                    onBack()
                }) { Text("←") } },
                actions = {
                    TextButton(onClick = {
                        val updated = buildOpening()
                        opening = updated
                        vm.saveOpening(projectId, updated)
                        savedFlash = true
                    }) { Text(if (savedFlash) "Guardado ✓" else "Guardar") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCamera,
                text = { Text("Tomar foto") },
                icon = { Text("●") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Estado") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("PENDIENTE", "VERIFICAR", "RELEVADO", "APROBADO").forEach { value ->
                            FilterChip(
                                selected = status == value,
                                onClick = { status = value; savedFlash = false },
                                label = { Text(statusLabel(value)) }
                            )
                        }
                    }
                }
            }

            item {
                SectionCard("Acciones rápidas") {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { showIncidenceDialog = true },
                            label = { Text("Registrar incidencia") }
                        )
                        AssistChip(
                            onClick = {
                                val updated = buildOpening()
                                opening = updated
                                vm.saveOpeningDraft(projectId, updated)
                                vm.duplicateOpening(projectId, updated, onDuplicated)
                            },
                            label = { Text("Duplicar vano") }
                        )
                    }
                    Text(
                        "Duplicar copia medidas y tipo, pero reinicia controles y estado para obligar una nueva verificación.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            item {
                SectionCard("Identificación") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = code,
                            onValueChange = { code = it; savedFlash = false },
                            modifier = Modifier.weight(1f),
                            label = { Text("Código") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = type,
                            onValueChange = { type = it; savedFlash = false },
                            modifier = Modifier.weight(1f),
                            label = { Text("Tipo") },
                            singleLine = true
                        )
                    }
                }
            }

            item {
                SectionCard("Medidas principales · mm") {
                    NumericPair("Ancho", width, { width = it; savedFlash = false }, "Alto", height, { height = it; savedFlash = false })
                    NumericPair("Antepecho", sill, { sill = it; savedFlash = false }, "Espesor muro", wall, { wall = it; savedFlash = false })
                    NumericPair("Diagonal 1", d1, { d1 = it; savedFlash = false }, "Diagonal 2", d2, { d2 = it; savedFlash = false })
                    NumericPair("Profundidad", depth, { depth = it; savedFlash = false }, "Libre inferior", bottom, { bottom = it; savedFlash = false })
                }
            }

            item {
                SectionCard("Encuentros / holguras · mm") {
                    NumericPair("Izquierda", left, { left = it; savedFlash = false }, "Derecha", right, { right = it; savedFlash = false })
                    NumericPair("Superior", top, { top = it; savedFlash = false }, "Inferior", bottom, { bottom = it; savedFlash = false })
                }
            }

            item {
                SectionCard("Control del vano") {
                    Text("Tocá cada control para pasar entre No verificado → OK → Observar.", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CheckChip("Plomo", plumb) { plumb = nextCheck(plumb); savedFlash = false }
                        CheckChip("Nivel", level) { level = nextCheck(level); savedFlash = false }
                        CheckChip("Escuadra", square) { square = nextCheck(square); savedFlash = false }
                        CheckChip("Piso", floor) { floor = nextCheck(floor); savedFlash = false }
                        CheckChip("Revoque", plaster) { plaster = nextCheck(plaster); savedFlash = false }
                        CheckChip("Premarco", premarco) { premarco = nextCheck(premarco); savedFlash = false }
                    }
                }
            }

            item {
                SectionCard("Condiciones de obra") {
                    OutlinedTextField(
                        value = direction,
                        onValueChange = { direction = it; savedFlash = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Sentido / condición de apertura") }
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = interference,
                        onValueChange = { interference = it; savedFlash = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Interferencias / obstáculos") },
                        minLines = 2
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it; savedFlash = false },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Comentarios / incidencias") },
                        minLines = 3
                    )
                }
            }

            item {
                SectionCard("Evidencia fotográfica · ${evidence.size}") {
                    if (evidence.isEmpty()) {
                        Text(
                            "Todavía no hay fotos. La cámara detectará candidatos de vano automáticamente y luego podrás dibujar cotas sobre la imagen.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(items = evidence, key = { it.id }) { item ->
                                EvidenceThumb(item, branding.companyName, brandLogo) { onEditEvidence(item.id) }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        val updated = buildOpening()
                        opening = updated
                        vm.saveOpening(projectId, updated)
                        savedFlash = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Guardar relevamiento")
                }
            }

            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showIncidenceDialog) {
        OpeningIncidenceDialog(
            onDismiss = { showIncidenceDialog = false },
            onSave = { title, detail, severity ->
                vm.addOpeningEvent(projectId, openingId, title, detail, severity)
                showIncidenceDialog = false
            }
        )
    }
}

@Composable
fun EvidenceEditorScreen(
    vm: AppViewModel,
    projectId: Long,
    evidenceId: Long,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onShareImage: (java.io.File) -> Unit
) {
    var item by remember(evidenceId) { mutableStateOf<EvidenceEntity?>(null) }
    var project by remember(evidenceId) { mutableStateOf<ProjectEntity?>(null) }
    var space by remember(evidenceId) { mutableStateOf<com.illu.relevametal.data.SpaceEntity?>(null) }
    var opening by remember(evidenceId) { mutableStateOf<OpeningEntity?>(null) }
    var caption by remember { mutableStateOf("") }
    var phase by remember { mutableStateOf("GENERAL") }
    var activeTool by remember { mutableStateOf(AnnotationTool.NONE) }
    var firstPoint by remember { mutableStateOf<Offset?>(null) }
    var pendingMeasurement by remember { mutableStateOf<Pair<Offset, Offset>?>(null) }
    var pendingTextPoint by remember { mutableStateOf<Offset?>(null) }
    var measurements by remember { mutableStateOf<List<MeasurementAnnotation>>(emptyList()) }
    var markups by remember { mutableStateOf<List<MarkupAnnotation>>(emptyList()) }
    var showDelete by remember { mutableStateOf(false) }
    var shareError by remember { mutableStateOf<String?>(null) }
    val branding by vm.branding.collectAsState()

    LaunchedEffect(evidenceId) {
        val loaded = vm.evidenceItem(evidenceId)
        item = loaded
        caption = loaded?.caption.orEmpty()
        phase = loaded?.phase ?: "GENERAL"
        measurements = AnnotationCodec.decodeMeasurements(loaded?.annotationJson.orEmpty())
        markups = AnnotationCodec.decodeMarkups(loaded?.annotationJson.orEmpty())
        project = vm.project(projectId)
        opening = loaded?.let { vm.opening(it.openingId) }
        space = opening?.let { vm.space(it.spaceId) }
    }

    val ev = item ?: return
    val bitmapState = rememberPhotoBitmap(ev.filePath, maxSide = 1800, rotationDegrees = ev.rotationDegrees)
    val sourceSizeState = rememberPhotoDimensions(ev.filePath)
    val logo by rememberPhotoBitmap(branding.logoPath, maxSide = 320)
    val bitmap = bitmapState.value
    val sourceSize = sourceSizeState.value
    val detections = remember(ev.detectedJson, sourceSize, ev.rotationDegrees) {
        if (sourceSize == null) emptyList() else AnnotationCodec.rotateDetections(
            AnnotationCodec.parseDetections(ev.detectedJson, sourceSize.width, sourceSize.height),
            ev.rotationDegrees
        )
    }
    val displayMeasurements = remember(measurements, ev.rotationDegrees) {
        AnnotationCodec.rotateMeasurements(measurements, ev.rotationDegrees)
    }
    val displayMarkups = remember(markups, ev.rotationDegrees) {
        AnnotationCodec.rotateMarkups(markups, ev.rotationDegrees)
    }
    val stampLines = remember(project, space, opening, ev.createdAt, phase, branding) {
        buildList {
            if (branding.showProject) project?.name?.takeIf { it.isNotBlank() }?.let { add("Obra: $it") }
            if (branding.showSpace) space?.name?.takeIf { it.isNotBlank() }?.let { add("Sector: $it") }
            if (branding.showOpening) opening?.let { add("Vano: ${it.code} · ${phaseLabel(phase)}") }
            if (branding.showTimestamp) add(dateFormat.format(Date(ev.createdAt)))
        }
    }

    fun persist(logChange: Boolean = false) {
        val updated = ev.copy(
            caption = caption.trim(),
            phase = phase,
            annotationJson = AnnotationCodec.encode(measurements, markups)
        )
        item = updated
        vm.saveEvidence(projectId, updated, logChange)
    }

    fun rotateBy(delta: Int) {
        val updated = ev.copy(
            caption = caption.trim(),
            phase = phase,
            annotationJson = AnnotationCodec.encode(measurements, markups),
            rotationDegrees = AnnotationCodec.normalizeRotation(ev.rotationDegrees + delta)
        )
        item = updated
        firstPoint = null
        vm.saveEvidence(projectId, updated)
    }

    fun toOriginal(point: Offset): Offset {
        val p = AnnotationCodec.inverseRotatePoint(point.x, point.y, ev.rotationDegrees)
        return Offset(p.first, p.second)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Foto técnica")
                        Text(phaseLabel(phase), style = MaterialTheme.typography.labelMedium)
                    }
                },
                navigationIcon = { TextButton(onClick = { persist(); onBack() }) { Text("←") } },
                actions = {
                    TextButton(onClick = { persist(logChange = true) }) { Text("Guardar") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black)
            ) {
                if (bitmap == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    AnnotatedPhoto(
                        bitmap = bitmap,
                        detections = detections,
                        measurements = displayMeasurements,
                        markups = displayMarkups,
                        activeTool = activeTool,
                        firstPoint = firstPoint,
                        onFirstPoint = { firstPoint = it },
                        onPair = { a, b ->
                            val oa = toOriginal(a)
                            val ob = toOriginal(b)
                            when (activeTool) {
                                AnnotationTool.MEASURE -> pendingMeasurement = oa to ob
                                AnnotationTool.ARROW -> markups = markups + MarkupAnnotation("ARROW", oa.x, oa.y, ob.x, ob.y)
                                AnnotationTool.RECTANGLE -> markups = markups + MarkupAnnotation("RECTANGLE", oa.x, oa.y, ob.x, ob.y)
                                AnnotationTool.CIRCLE -> markups = markups + MarkupAnnotation("CIRCLE", oa.x, oa.y, ob.x, ob.y)
                                else -> Unit
                            }
                            firstPoint = null
                            if (activeTool != AnnotationTool.MEASURE) persist()
                        },
                        onSinglePoint = { point ->
                            if (activeTool == AnnotationTool.TEXT) pendingTextPoint = toOriginal(point)
                        },
                        stamp = PhotoStampUi(
                            companyName = branding.companyName.trim(),
                            lines = stampLines,
                            logo = logo,
                            enabled = branding.stampEnabled
                        ),
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Herramientas", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AnnotationToolChip("Cota", AnnotationTool.MEASURE, activeTool) { activeTool = it; firstPoint = null }
                    AnnotationToolChip("Flecha", AnnotationTool.ARROW, activeTool) { activeTool = it; firstPoint = null }
                    AnnotationToolChip("Rectángulo", AnnotationTool.RECTANGLE, activeTool) { activeTool = it; firstPoint = null }
                    AnnotationToolChip("Círculo", AnnotationTool.CIRCLE, activeTool) { activeTool = it; firstPoint = null }
                    AnnotationToolChip("Texto", AnnotationTool.TEXT, activeTool) { activeTool = it; firstPoint = null }
                }

                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { rotateBy(-90) }, label = { Text("Girar ↺") })
                    AssistChip(onClick = { rotateBy(90) }, label = { Text("Girar ↻") })
                    AssistChip(
                        enabled = measurements.isNotEmpty(),
                        onClick = { if (measurements.isNotEmpty()) { measurements = measurements.dropLast(1); persist() } },
                        label = { Text("Deshacer cota") }
                    )
                    AssistChip(
                        enabled = markups.isNotEmpty(),
                        onClick = { if (markups.isNotEmpty()) { markups = markups.dropLast(1); persist() } },
                        label = { Text("Deshacer marca") }
                    )
                    AssistChip(
                        onClick = {
                            val updated = ev.copy(
                                caption = caption,
                                phase = phase,
                                annotationJson = AnnotationCodec.encode(measurements, markups),
                                isPrimary = true
                            )
                            item = updated
                            vm.setPrimaryEvidence(projectId, updated)
                        },
                        label = { Text(if (ev.isPrimary) "Principal ✓" else "Usar de portada") }
                    )
                    AssistChip(
                        onClick = {
                            val exportItem = ev.copy(
                                caption = caption.trim(),
                                phase = phase,
                                annotationJson = AnnotationCodec.encode(measurements, markups)
                            )
                            item = exportItem
                            vm.saveEvidence(projectId, exportItem)
                            vm.exportEvidenceImage(projectId, exportItem) { result ->
                                result.onSuccess(onShareImage).onFailure { shareError = it.message ?: "No se pudo compartir la foto" }
                            }
                        },
                        label = { Text("Compartir foto") }
                    )
                    AssistChip(onClick = { showDelete = true }, label = { Text("Eliminar") })
                }

                Text("Etapa de la foto", fontWeight = FontWeight.SemiBold)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("GENERAL", "INICIAL", "INCIDENCIA", "CORRECCION", "FINAL").forEach { value ->
                        FilterChip(
                            selected = phase == value,
                            onClick = { phase = value; persist() },
                            label = { Text(phaseLabel(value)) }
                        )
                    }
                }

                val toolHint = when (activeTool) {
                    AnnotationTool.NONE -> "Seleccioná una herramienta para marcar la foto."
                    AnnotationTool.TEXT -> "Tocá el punto donde querés agregar la observación."
                    else -> if (firstPoint == null) "Tocá el primer punto." else "Ahora tocá el segundo punto."
                }
                Text(
                    "$toolHint · Detección: ${detections.size} · Cotas: ${measurements.size} · Marcas: ${markups.size} · Giro: ${ev.rotationDegrees}°",
                    style = MaterialTheme.typography.labelMedium
                )

                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Comentario de esta foto") },
                    minLines = 2
                )
            }
        }
    }

    pendingMeasurement?.let { pair ->
        MeasurementDialog(
            onDismiss = { pendingMeasurement = null },
            onSave = { label, value ->
                measurements = measurements + MeasurementAnnotation(
                    x1 = pair.first.x,
                    y1 = pair.first.y,
                    x2 = pair.second.x,
                    y2 = pair.second.y,
                    label = label,
                    value = value
                )
                pendingMeasurement = null
                persist()
            }
        )
    }

    pendingTextPoint?.let { point ->
        TextMarkupDialog(
            onDismiss = { pendingTextPoint = null },
            onSave = { text ->
                markups = markups + MarkupAnnotation("TEXT", point.x, point.y, point.x, point.y, text)
                pendingTextPoint = null
                persist()
            }
        )
    }

    shareError?.let { message ->
        AlertDialog(
            onDismissRequest = { shareError = null },
            title = { Text("No se pudo compartir") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { shareError = null }) { Text("Aceptar") } }
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminar fotografía") },
            text = { Text("La imagen se eliminará de este relevamiento.") },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Cancelar") } },
            confirmButton = {
                Button(onClick = {
                    vm.deleteEvidence(projectId, ev)
                    showDelete = false
                    onDeleted()
                }) { Text("Eliminar") }
            }
        )
    }
}

@Composable
private fun AnnotationToolChip(
    label: String,
    tool: AnnotationTool,
    active: AnnotationTool,
    onChange: (AnnotationTool) -> Unit
) {
    FilterChip(
        selected = active == tool,
        onClick = { onChange(if (active == tool) AnnotationTool.NONE else tool) },
        label = { Text(label) }
    )
}

@Composable
private fun TextMarkupDialog(onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Texto sobre la foto") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Observación") },
                minLines = 2
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(enabled = text.isNotBlank(), onClick = { onSave(text.trim()) }) { Text("Agregar") }
        }
    )
}

private fun phaseLabel(value: String): String = when (value) {
    "INICIAL" -> "Inicial"
    "INCIDENCIA" -> "Incidencia"
    "CORRECCION" -> "Corrección"
    "FINAL" -> "Final"
    else -> "General"
}

@Composable
private fun EvidenceThumb(
    item: EvidenceEntity,
    companyName: String,
    logo: ImageBitmap?,
    onClick: () -> Unit
) {
    val bitmap by rememberPhotoBitmap(item.filePath, maxSide = 420, rotationDegrees = item.rotationDegrees)
    ElevatedCard(
        Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(116.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
                if (item.isPrimary) {
                    Surface(
                        modifier = Modifier.padding(6.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text("Principal", Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (logo != null || companyName.isNotBlank()) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .background(Color(0xB8000000), RoundedCornerShape(7.dp))
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        logo?.let {
                            androidx.compose.foundation.Image(
                                bitmap = it,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        }
                        if (companyName.isNotBlank()) {
                            Text(companyName, color = Color.White, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            Column(Modifier.padding(10.dp)) {
                Text(phaseLabel(item.phase), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (item.caption.isBlank()) "Abrir / documentar" else item.caption,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun Timeline(events: List<EventEntity>) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (events.isEmpty()) {
            item { EmptyState("Bitácora vacía", "Cada foto, cambio y observación importante puede quedar registrado con fecha y hora.") }
        }
        items(items = events, key = { it.id }) { event ->
            Surface(
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(Modifier.padding(14.dp)) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(eventColor(event.severity), RoundedCornerShape(50))
                            .align(Alignment.CenterVertically)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(event.title, fontWeight = FontWeight.SemiBold)
                        if (event.detail.isNotBlank()) {
                            Text(event.detail, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            dateFormat.format(Date(event.createdAt)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun ProjectInfoEditor(
    vm: AppViewModel,
    project: ProjectEntity?,
    referencePhotos: List<ProjectReferencePhotoEntity>,
    onSave: (ProjectEntity) -> Unit
) {
    if (project == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    var client by remember(project.id, project.updatedAt) { mutableStateOf(project.client) }
    var address by remember(project.id, project.updatedAt) { mutableStateOf(project.address) }
    var responsible by remember(project.id, project.updatedAt) { mutableStateOf(project.responsible) }
    var notes by remember(project.id, project.updatedAt) { mutableStateOf(project.notes) }
    var status by remember(project.id, project.updatedAt) { mutableStateOf(project.status) }
    var referenceError by remember { mutableStateOf<String?>(null) }
    var importingReferences by remember { mutableStateOf(false) }

    val referenceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) {
            importingReferences = true
            referenceError = null
            vm.addProjectReferencePhotos(project.id, uris) { result ->
                importingReferences = false
                result.onFailure { referenceError = it.message ?: "No se pudieron agregar las imágenes" }
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            SectionCard("Fotos del edificio · obligatorias para el PDF") {
                Text(
                    "Agregá una o más imágenes generales del edificio. Se usarán como referencia visual en la portada y el PDF no se generará si no hay ninguna.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (referencePhotos.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Falta al menos una imagen de referencia del edificio.",
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(
                        "${referencePhotos.size} imagen(es) cargada(s). La portada mostrará hasta tres.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(items = referencePhotos, key = { it.id }) { photo ->
                            ProjectReferencePhotoCard(photo) { vm.deleteProjectReferencePhoto(project.id, photo) }
                        }
                    }
                }
                Button(
                    onClick = { referenceLauncher.launch("image/*") },
                    enabled = !importingReferences
                ) {
                    Text(if (importingReferences) "Agregando…" else "Agregar imágenes del edificio")
                }
                referenceError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("EN_CURSO", "PAUSADA", "FINALIZADA").forEach { value ->
                    FilterChip(
                        selected = status == value,
                        onClick = { status = value },
                        label = { Text(statusLabel(value)) }
                    )
                }
            }
        }
        item { OutlinedTextField(client, { client = it }, Modifier.fillMaxWidth(), label = { Text("Cliente") }) }
        item { OutlinedTextField(address, { address = it }, Modifier.fillMaxWidth(), label = { Text("Dirección") }) }
        item { OutlinedTextField(responsible, { responsible = it }, Modifier.fillMaxWidth(), label = { Text("Responsable / contacto") }) }
        item { OutlinedTextField(notes, { notes = it }, Modifier.fillMaxWidth(), label = { Text("Observaciones generales") }, minLines = 4) }
        item {
            Button(
                onClick = {
                    onSave(project.copy(client = client, address = address, responsible = responsible, notes = notes, status = status))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Guardar datos de obra") }
        }
    }
}

@Composable
private fun ProjectReferencePhotoCard(
    photo: ProjectReferencePhotoEntity,
    onDelete: () -> Unit
) {
    val bitmap by rememberPhotoBitmap(photo.filePath, maxSide = 480)
    ElevatedCard(Modifier.width(150.dp)) {
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(105.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it,
                        contentDescription = "Referencia del edificio",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                }
            }
            TextButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) { Text("Eliminar") }
        }
    }
}

@Composable
private fun BrandingDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit
) {
    val current by vm.branding.collectAsState()
    var draft by remember(current) { mutableStateOf(current) }
    var importError by remember { mutableStateOf<String?>(null) }
    val logo by rememberPhotoBitmap(draft.logoPath, maxSide = 360)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            vm.importBrandLogo(uri) { result ->
                result.onFailure { importError = it.message ?: "No se pudo cargar el logo" }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Marca en fotografías") },
        confirmButton = {
            Button(onClick = { vm.updateBranding(draft); onDismiss() }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (logo != null) {
                            androidx.compose.foundation.Image(
                                bitmap = logo!!,
                                contentDescription = "Logo de empresa",
                                modifier = Modifier.size(64.dp),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                                Text("Sin logo", Modifier.padding(14.dp), style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            if (draft.companyName.isNotBlank()) {
                                Text(draft.companyName, fontWeight = FontWeight.Bold)
                            } else {
                                Text("Sin nombre de empresa", style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                if (draft.logoPath.isBlank()) "Sin logo personalizado" else "Logo personalizado cargado",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = draft.companyName,
                    onValueChange = { draft = draft.copy(companyName = it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nombre de empresa (opcional)") },
                    singleLine = true
                )
                Text(
                    "Podés dejarlo vacío para usar solo el logo o mostrar el sello sin nombre de empresa.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { launcher.launch("image/*") }) { Text("Elegir logo") }
                    if (draft.logoPath.isNotBlank()) {
                        OutlinedButton(onClick = { vm.clearBrandLogo(); draft = draft.copy(logoPath = "") }) { Text("Quitar") }
                    }
                }

                Text("El JPG original siempre queda intacto. La marca se aplica al visualizar, exportar y generar el PDF.", style = MaterialTheme.typography.bodySmall)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Mostrar sello", Modifier.weight(1f))
                    Switch(draft.stampEnabled, { draft = draft.copy(stampEnabled = it) })
                }
                BrandToggle("Fecha y hora", draft.showTimestamp) { draft = draft.copy(showTimestamp = it) }
                BrandToggle("Nombre de obra", draft.showProject) { draft = draft.copy(showProject = it) }
                BrandToggle("Espacio / sector", draft.showSpace) { draft = draft.copy(showSpace = it) }
                BrandToggle("Código de vano", draft.showOpening) { draft = draft.copy(showOpening = it) }

                importError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    )
}

@Composable
private fun BrandToggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChecked)
    }
}

@Composable
private fun NewProjectDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var client by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var responsible by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva obra") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre de obra *") })
                OutlinedTextField(client, { client = it }, label = { Text("Cliente") })
                OutlinedTextField(address, { address = it }, label = { Text("Dirección") })
                OutlinedTextField(responsible, { responsible = it }, label = { Text("Responsable / contacto") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones iniciales") }, minLines = 2)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = { onSave(name, client, address, responsible, notes) }
            ) { Text("Crear") }
        }
    )
}

@Composable
private fun NewSpaceDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("") }
    var sector by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo espacio / sector") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre *") })
                OutlinedTextField(level, { level = it }, label = { Text("Planta / nivel") })
                OutlinedTextField(sector, { sector = it }, label = { Text("Sector / fachada") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, minLines = 2)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(enabled = name.isNotBlank(), onClick = { onSave(name, level, sector, notes) }) {
                Text("Crear")
            }
        }
    )
}

@Composable
private fun NewOpeningDialog(
    initialCode: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var code by remember(initialCode) { mutableStateOf(initialCode) }
    var type by remember { mutableStateOf("VANO") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo vano") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(code, { code = it }, label = { Text("Código") }, singleLine = true)
                OutlinedTextField(type, { type = it }, label = { Text("Tipo (ventana, puerta, paño fijo…)") })
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(enabled = code.isNotBlank(), onClick = { onSave(code, type) }) { Text("Crear y abrir") }
        }
    )
}

@Composable
private fun NewEventDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("INFO") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar en bitácora") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("INFO", "ALERTA", "DECISION").forEach { value ->
                        FilterChip(
                            selected = severity == value,
                            onClick = { severity = value },
                            label = { Text(if (value == "DECISION") "Decisión" else value.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Título *") })
                OutlinedTextField(detail, { detail = it }, label = { Text("Detalle") }, minLines = 3)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(enabled = title.isNotBlank(), onClick = { onSave(title, detail, severity) }) { Text("Registrar") }
        }
    )
}

@Composable
private fun OpeningIncidenceDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var detail by remember { mutableStateOf("") }
    var severity by remember { mutableStateOf("ALERTA") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar incidencia") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Título") },
                    placeholder = { Text("Ej. vano fuera de escuadra") }
                )
                OutlinedTextField(
                    value = detail,
                    onValueChange = { detail = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Detalle / acción requerida") },
                    minLines = 3
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("INFO", "ALERTA", "DECISION").forEach { value ->
                        FilterChip(
                            selected = severity == value,
                            onClick = { severity = value },
                            label = { Text(if (value == "DECISION") "Decisión" else value.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(
                enabled = title.isNotBlank(),
                onClick = { onSave(title.trim(), detail.trim(), severity) }
            ) { Text("Registrar") }
        }
    )
}

@Composable
private fun MeasurementDialog(
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var label by remember { mutableStateOf("Cota") }
    var value by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva cota sobre foto") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(label, { label = it }, label = { Text("Nombre") })
                OutlinedTextField(
                    value,
                    { value = it },
                    label = { Text("Valor (ej. 1250 mm)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        confirmButton = {
            Button(onClick = { onSave(label, value) }) { Text("Agregar") }
        }
    )
}

@Composable
private fun NumericPair(
    label1: String,
    value1: String,
    onValue1: (String) -> Unit,
    label2: String,
    value2: String,
    onValue2: (String) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value1,
            onValueChange = { onValue1(it.filter { ch -> ch.isDigit() }) },
            modifier = Modifier.weight(1f),
            label = { Text(label1) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
        OutlinedTextField(
            value = value2,
            onValueChange = { onValue2(it.filter { ch -> ch.isDigit() }) },
            modifier = Modifier.weight(1f),
            label = { Text(label2) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )
    }
}

@Composable
private fun CheckChip(label: String, state: String, onClick: () -> Unit) {
    val suffix = when (state) {
        "OK" -> "✓"
        "OBSERVAR" -> "!"
        else -> "—"
    }
    FilterChip(
        selected = state == "OK",
        onClick = onClick,
        label = { Text("$label $suffix") }
    )
}

private fun nextCheck(value: String): String = when (value) {
    "NO_VERIFICADO" -> "OK"
    "OK" -> "OBSERVAR"
    else -> "NO_VERIFICADO"
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusBadge(status: String) {
    val bg = when (status) {
        "APROBADO" -> MaterialTheme.colorScheme.primaryContainer
        "RELEVADO" -> MaterialTheme.colorScheme.secondaryContainer
        "VERIFICAR", "PAUSADA" -> MaterialTheme.colorScheme.tertiaryContainer
        "FINALIZADA" -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = RoundedCornerShape(50), color = bg) {
        Text(
            statusLabel(status),
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

private fun statusLabel(status: String): String = when (status) {
    "TODAS" -> "Todas"
    "EN_CURSO" -> "En curso"
    "PAUSADA" -> "Pausada"
    "FINALIZADA" -> "Finalizada"
    "PENDIENTE" -> "Pendiente"
    "VERIFICAR" -> "Verificar"
    "RELEVADO" -> "Relevado"
    "APROBADO" -> "Aprobado"
    else -> status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}

@Composable
private fun eventColor(severity: String): Color = when (severity) {
    "ALERTA" -> MaterialTheme.colorScheme.error
    "DECISION" -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}

