package com.illu.relevametal.ui

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illu.relevametal.branding.BrandingSettings
import com.illu.relevametal.branding.BrandingStore
import com.illu.relevametal.data.*
import com.illu.relevametal.detection.OpeningDetector
import com.illu.relevametal.pdf.ReportPdf
import com.illu.relevametal.render.PhotoRenderer
import com.illu.relevametal.transfer.ProjectArchiveManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class ProjectStats(
    val spaces: Int = 0,
    val openings: Int = 0,
    val pending: Int = 0,
    val photos: Int = 0
)


data class PdfOpeningPhotoChoice(
    val openingId: Long,
    val openingCode: String,
    val spaceName: String,
    val photos: List<EvidenceEntity>
) {
    val defaultEvidenceId: Long?
        get() = photos.maxByOrNull { it.createdAt }?.id
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val db = AppDatabase.get(app)
    private val detector = OpeningDetector()
    private val brandingStore = BrandingStore(app)
    private val archiveManager = ProjectArchiveManager(app, db)
    val branding = MutableStateFlow(brandingStore.load())

    val projects = db.projects()
        .observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun spaces(projectId: Long) = db.spaces().observe(projectId)
    fun openings(spaceId: Long) = db.openings().observe(spaceId)
    fun evidence(openingId: Long) = db.evidence().observe(openingId)
    fun events(projectId: Long) = db.events().observe(projectId)
    fun projectReferencePhotos(projectId: Long) = db.projectReferencePhotos().observe(projectId)

    fun projectStats(projectId: Long): Flow<ProjectStats> = combine(
        db.spaces().count(projectId),
        db.openings().countForProject(projectId),
        db.openings().pendingForProject(projectId),
        db.evidence().countForProject(projectId)
    ) { spaces, openings, pending, photos ->
        ProjectStats(spaces, openings, pending, photos)
    }

    suspend fun project(id: Long) = db.projects().project(id)
    suspend fun space(id: Long) = db.spaces().get(id)
    suspend fun opening(id: Long) = db.openings().get(id)
    suspend fun evidenceItem(id: Long) = db.evidence().get(id)

    fun updateBranding(settings: BrandingSettings) {
        val normalized = settings.copy(companyName = settings.companyName.trim())
        brandingStore.save(normalized)
        branding.value = normalized
    }

    fun importBrandLogo(uri: Uri, onReady: (Result<Unit>) -> Unit = {}) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val dir = File(getApplication<Application>().filesDir, "branding").apply { mkdirs() }
                val target = File(dir, "company_logo")
                getApplication<Application>().contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "No se pudo leer el logo seleccionado" }
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(target.absolutePath, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0) { "El archivo seleccionado no es una imagen válida" }
                val updated = branding.value.copy(logoPath = target.absolutePath)
                brandingStore.save(updated)
                updated
            }
        }
        result.onSuccess { branding.value = it }
        onReady(result.map { Unit })
    }

    fun clearBrandLogo() {
        branding.value.logoPath.takeIf { it.isNotBlank() }?.let { runCatching { File(it).delete() } }
        val updated = branding.value.copy(logoPath = "")
        brandingStore.save(updated)
        branding.value = updated
    }

    fun addProjectReferencePhotos(
        projectId: Long,
        uris: List<Uri>,
        onReady: (Result<Unit>) -> Unit = {}
    ) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                require(uris.isNotEmpty()) { "Seleccioná al menos una imagen del edificio" }
                val resolver = getApplication<Application>().contentResolver
                val dir = File(getApplication<Application>().filesDir, "project_reference_photos/$projectId").apply { mkdirs() }
                uris.forEachIndexed { index, uri ->
                    val mime = resolver.getType(uri).orEmpty()
                    val extension = when {
                        mime.contains("png", ignoreCase = true) -> "png"
                        mime.contains("webp", ignoreCase = true) -> "webp"
                        mime.contains("heic", ignoreCase = true) || mime.contains("heif", ignoreCase = true) -> "heic"
                        else -> "jpg"
                    }
                    val target = File(dir, "building_${System.currentTimeMillis()}_${index}.$extension")
                    resolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "No se pudo leer una de las imágenes seleccionadas" }
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(target.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        target.delete()
                        error("Una de las imágenes seleccionadas no es válida")
                    }
                    db.projectReferencePhotos().insert(
                        ProjectReferencePhotoEntity(
                            projectId = projectId,
                            filePath = target.absolutePath
                        )
                    )
                }
                touchProject(projectId)
            }
        }
        onReady(result)
    }

    fun deleteProjectReferencePhoto(projectId: Long, item: ProjectReferencePhotoEntity) =
        viewModelScope.launch(Dispatchers.IO) {
            db.projectReferencePhotos().delete(item)
            runCatching { File(item.filePath).delete() }
            touchProject(projectId)
        }

    fun addProject(
        name: String,
        client: String,
        address: String,
        responsible: String,
        notes: String,
        onCreated: ((Long) -> Unit)? = null
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = db.projects().insert(
            ProjectEntity(
                name = name.trim(),
                client = client.trim(),
                address = address.trim(),
                responsible = responsible.trim(),
                notes = notes.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        db.events().insert(
            EventEntity(
                projectId = id,
                kind = "CREATED",
                title = "Obra creada",
                detail = name.trim()
            )
        )
        onCreated?.invoke(id)
    }

    fun updateProject(project: ProjectEntity) = viewModelScope.launch {
        db.projects().update(project.copy(updatedAt = System.currentTimeMillis()))
    }

    fun addSpace(
        projectId: Long,
        name: String,
        level: String = "",
        sector: String = "",
        notes: String = "",
        onCreated: ((Long) -> Unit)? = null
    ) = viewModelScope.launch {
        val now = System.currentTimeMillis()
        val id = db.spaces().insert(
            SpaceEntity(
                projectId = projectId,
                name = name.trim(),
                level = level.trim(),
                sector = sector.trim(),
                notes = notes.trim(),
                createdAt = now,
                updatedAt = now
            )
        )
        touchProject(projectId)
        db.events().insert(
            EventEntity(
                projectId = projectId,
                kind = "SPACE",
                title = "Espacio agregado",
                detail = name.trim()
            )
        )
        onCreated?.invoke(id)
    }

    suspend fun nextOpeningCode(spaceId: Long): String {
        val existing = db.openings().list(spaceId)
            .map { it.code.uppercase() }
            .toSet()
        var n = 1
        while (true) {
            val candidate = "V%02d".format(n)
            if (candidate !in existing) return candidate
            n++
        }
    }

    fun addOpening(
        projectId: Long,
        spaceId: Long,
        code: String,
        type: String,
        onCreated: ((Long) -> Unit)? = null
    ) = viewModelScope.launch {
        val id = db.openings().insert(
            OpeningEntity(
                spaceId = spaceId,
                code = code.trim().uppercase(),
                type = type.trim().uppercase().ifBlank { "VANO" }
            )
        )
        touchProject(projectId)
        db.events().insert(
            EventEntity(
                projectId = projectId,
                openingId = id,
                kind = "OPENING",
                title = "Vano agregado",
                detail = code.trim().uppercase()
            )
        )
        onCreated?.invoke(id)
    }


    fun duplicateOpening(
        projectId: Long,
        source: OpeningEntity,
        onCreated: (Long) -> Unit
    ) = viewModelScope.launch {
        val code = nextOpeningCode(source.spaceId)
        val id = db.openings().insert(
            source.copy(
                id = 0,
                code = code,
                status = "PENDIENTE",
                plumbState = "NO_VERIFICADO",
                levelState = "NO_VERIFICADO",
                squareState = "NO_VERIFICADO",
                floorState = "NO_VERIFICADO",
                plasterState = "NO_VERIFICADO",
                premarcoState = "NO_VERIFICADO",
                interference = "",
                notes = "",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
        touchProject(projectId)
        db.events().insert(
            EventEntity(
                projectId = projectId,
                openingId = id,
                kind = "OPENING_DUPLICATED",
                title = "$code: vano duplicado",
                detail = "Base copiada desde ${source.code}; controles reiniciados para nueva verificación."
            )
        )
        onCreated(id)
    }

    fun addOpeningEvent(
        projectId: Long,
        openingId: Long,
        title: String,
        detail: String,
        severity: String = "ALERTA"
    ) = viewModelScope.launch {
        val code = db.openings().get(openingId)?.code ?: "Vano"
        db.events().insert(
            EventEntity(
                projectId = projectId,
                openingId = openingId,
                kind = "INCIDENCE",
                title = "$code: ${title.trim()}",
                detail = detail.trim(),
                severity = severity
            )
        )
        touchProject(projectId)
    }


    fun saveOpeningDraft(projectId: Long, opening: OpeningEntity) = viewModelScope.launch {
        val old = db.openings().get(opening.id)
        val updated = opening.copy(updatedAt = System.currentTimeMillis())
        db.openings().update(updated)
        touchProject(projectId)
        if (old != null && old.status != updated.status) {
            db.events().insert(
                EventEntity(
                    projectId = projectId,
                    openingId = updated.id,
                    kind = "STATUS",
                    title = "${updated.code}: ${updated.status}"
                )
            )
        }
    }

    fun saveOpening(projectId: Long, opening: OpeningEntity) = viewModelScope.launch {
        val old = db.openings().get(opening.id)
        val updated = opening.copy(updatedAt = System.currentTimeMillis())
        db.openings().update(updated)
        touchProject(projectId)

        val statusChanged = old != null && old.status != updated.status
        db.events().insert(
            EventEntity(
                projectId = projectId,
                openingId = updated.id,
                kind = if (statusChanged) "STATUS" else "MEASURE",
                title = if (statusChanged) {
                    "${updated.code}: ${updated.status}"
                } else {
                    "${updated.code}: relevamiento actualizado"
                },
                detail = buildString {
                    if (updated.widthMm != null && updated.heightMm != null) {
                        append("${updated.widthMm} × ${updated.heightMm} mm")
                    }
                    if (updated.notes.isNotBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(updated.notes.take(120))
                    }
                }
            )
        )
    }

    fun addEvidence(
        projectId: Long,
        openingId: Long,
        path: String,
        onReady: (Long) -> Unit
    ) = viewModelScope.launch(Dispatchers.IO) {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        val largest = maxOf(bounds.outWidth, bounds.outHeight)
        while (largest / sample > 1600 * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        val bitmap = BitmapFactory.decodeFile(path, options)
        val detected = bitmap?.let { detector.detect(it) }.orEmpty()
        val scaleX = if (bitmap != null && bitmap.width > 0) bounds.outWidth.toFloat() / bitmap.width else 1f
        val scaleY = if (bitmap != null && bitmap.height > 0) bounds.outHeight.toFloat() / bitmap.height else 1f
        val json = detected.joinToString(prefix = "[", postfix = "]") { c ->
            "{\"l\":${c.rect.left * scaleX},\"t\":${c.rect.top * scaleY},\"r\":${c.rect.right * scaleX},\"b\":${c.rect.bottom * scaleY},\"confidence\":${c.confidence}}"
        }
        bitmap?.recycle()
        val id = db.evidence().insert(
            EvidenceEntity(
                openingId = openingId,
                filePath = path,
                detectedJson = json
            )
        )
        touchProject(projectId)
        val code = db.openings().get(openingId)?.code ?: "Vano"
        db.events().insert(
            EventEntity(
                projectId = projectId,
                openingId = openingId,
                kind = "PHOTO",
                title = "$code: fotografía agregada",
                detail = if (detected.isEmpty()) {
                    "Sin detección automática"
                } else {
                    "${detected.size} candidato(s) de vano detectado(s)"
                }
            )
        )
        withContext(Dispatchers.Main) { onReady(id) }
    }

    fun saveEvidence(
        projectId: Long,
        item: EvidenceEntity,
        logChange: Boolean = false
    ) = viewModelScope.launch {
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        db.evidence().update(updated)
        touchProject(projectId)
        if (logChange) {
            val code = db.openings().get(item.openingId)?.code ?: "Vano"
            db.events().insert(
                EventEntity(
                    projectId = projectId,
                    openingId = item.openingId,
                    kind = "ANNOTATION",
                    title = "$code: foto documentada",
                    detail = item.caption.take(140)
                )
            )
        }
    }

    fun setPrimaryEvidence(projectId: Long, item: EvidenceEntity) = viewModelScope.launch {
        db.evidence().clearPrimary(item.openingId)
        db.evidence().update(item.copy(isPrimary = true, updatedAt = System.currentTimeMillis()))
        touchProject(projectId)
    }

    fun deleteEvidence(projectId: Long, item: EvidenceEntity) = viewModelScope.launch(Dispatchers.IO) {
        runCatching { File(item.filePath).delete() }
        db.evidence().delete(item)
        touchProject(projectId)
        val code = db.openings().get(item.openingId)?.code ?: "Vano"
        db.events().insert(
            EventEntity(
                projectId = projectId,
                openingId = item.openingId,
                kind = "PHOTO_DELETED",
                title = "$code: fotografía eliminada"
            )
        )
    }

    fun addEvent(
        projectId: Long,
        title: String,
        detail: String,
        severity: String
    ) = viewModelScope.launch {
        db.events().insert(
            EventEntity(
                projectId = projectId,
                kind = "NOTE",
                title = title.trim(),
                detail = detail.trim(),
                severity = severity
            )
        )
        touchProject(projectId)
    }

    fun loadPdfPhotoChoices(
        projectId: Long,
        onReady: (Result<List<PdfOpeningPhotoChoice>>) -> Unit
    ) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val project = db.projects().project(projectId) ?: error("Obra inexistente")
                val referencePhotos = db.projectReferencePhotos()
                    .list(project.id)
                    .filter { File(it.filePath).isFile }
                require(referencePhotos.isNotEmpty()) {
                    "Para generar el PDF agregá al menos una imagen válida del edificio en la pestaña Obra."
                }

                db.spaces().list(projectId).flatMap { space ->
                    db.openings().list(space.id).map { opening ->
                        val photos = db.evidence().list(opening.id)
                            .filter { File(it.filePath).isFile }
                            .sortedByDescending { it.createdAt }
                        PdfOpeningPhotoChoice(
                            openingId = opening.id,
                            openingCode = opening.code,
                            spaceName = space.name,
                            photos = photos
                        )
                    }
                }
            }
        }
        onReady(result)
    }

    fun exportProject(
        projectId: Long,
        selectedEvidenceIds: Map<Long, Long?>,
        onReady: (Result<File>) -> Unit
    ) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val project = db.projects().project(projectId) ?: error("Obra inexistente")
                val spaces = db.spaces().list(projectId).map { space ->
                    val openings = db.openings().list(space.id).map { opening ->
                        val allEvidence = db.evidence().list(opening.id)
                            .filter { File(it.filePath).isFile }
                        val selectedId = if (selectedEvidenceIds.containsKey(opening.id)) {
                            selectedEvidenceIds[opening.id]
                        } else {
                            allEvidence.maxByOrNull { it.createdAt }?.id
                        }
                        val selected = selectedId?.let { id ->
                            allEvidence.firstOrNull { it.id == id }
                        }
                        ReportPdf.OpeningBundle(opening, listOfNotNull(selected))
                    }
                    ReportPdf.SpaceBundle(space, openings)
                }
                val events = db.events().list(projectId)
                val referencePhotos = db.projectReferencePhotos()
                    .list(projectId)
                    .filter { File(it.filePath).isFile }
                require(referencePhotos.isNotEmpty()) {
                    "Para generar el PDF agregá al menos una imagen válida del edificio en la pestaña Obra."
                }
                ReportPdf(getApplication()).generate(project, spaces, events, branding.value, referencePhotos)
            }
        }
        onReady(result)
    }


    fun exportDataArchive(
        projectIds: List<Long>,
        onReady: (Result<File>) -> Unit
    ) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching { archiveManager.export(projectIds) }
        }
        onReady(result)
    }

    fun importDataArchive(
        uri: Uri,
        onReady: (Result<ProjectArchiveManager.ImportResult>) -> Unit
    ) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching { archiveManager.import(uri) }
        }
        onReady(result)
    }

    fun exportEvidenceImage(
        projectId: Long,
        evidence: EvidenceEntity,
        onReady: (Result<File>) -> Unit
    ) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val opening = db.openings().get(evidence.openingId) ?: error("Vano inexistente")
                val space = db.spaces().get(opening.spaceId) ?: error("Espacio inexistente")
                val project = db.projects().project(projectId) ?: error("Obra inexistente")
                val bitmap = PhotoRenderer.render(
                    evidence = evidence,
                    project = project,
                    space = space,
                    opening = opening,
                    branding = branding.value,
                    maxSide = 2600
                ) ?: error("No se pudo procesar la fotografía")
                val dir = File(getApplication<Application>().filesDir, "shared_photos").apply { mkdirs() }
                val out = File(dir, "GrupoIDEA_${opening.code}_${System.currentTimeMillis()}.jpg")
                FileOutputStream(out).use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 93, stream)
                }
                bitmap.recycle()
                out
            }
        }
        onReady(result)
    }

    private suspend fun touchProject(projectId: Long) {
        val project = db.projects().project(projectId) ?: return
        db.projects().update(project.copy(updatedAt = System.currentTimeMillis()))
    }
}
