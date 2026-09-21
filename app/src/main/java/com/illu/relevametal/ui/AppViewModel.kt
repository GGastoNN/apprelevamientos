package com.illu.relevametal.ui

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.illu.relevametal.data.*
import com.illu.relevametal.detection.OpeningDetector
import com.illu.relevametal.pdf.ReportPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ProjectStats(
    val spaces: Int = 0,
    val openings: Int = 0,
    val pending: Int = 0,
    val photos: Int = 0
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val db = AppDatabase.get(app)
    private val detector = OpeningDetector()

    val projects = db.projects()
        .observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun spaces(projectId: Long) = db.spaces().observe(projectId)
    fun openings(spaceId: Long) = db.openings().observe(spaceId)
    fun evidence(openingId: Long) = db.evidence().observe(openingId)
    fun events(projectId: Long) = db.events().observe(projectId)

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

    fun exportProject(
        projectId: Long,
        onReady: (Result<File>) -> Unit
    ) = viewModelScope.launch {
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val project = db.projects().project(projectId) ?: error("Obra inexistente")
                val spaces = db.spaces().list(projectId).map { space ->
                    val openings = db.openings().list(space.id).map { opening ->
                        ReportPdf.OpeningBundle(opening, db.evidence().list(opening.id))
                    }
                    ReportPdf.SpaceBundle(space, openings)
                }
                val events = db.events().list(projectId)
                ReportPdf(getApplication()).generate(project, spaces, events)
            }
        }
        onReady(result)
    }

    private suspend fun touchProject(projectId: Long) {
        val project = db.projects().project(projectId) ?: return
        db.projects().update(project.copy(updatedAt = System.currentTimeMillis()))
    }
}
