package com.illu.relevametal.transfer

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.room.withTransaction
import com.illu.relevametal.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Portable, versioned project archive used to move one or more works between devices.
 *
 * The archive intentionally stores the original photographs plus their annotation/detection
 * metadata instead of storing a second rendered copy. This keeps the package smaller while
 * preserving measurements, markups and every value needed by the app to render the photo again.
 */
class ProjectArchiveManager(
    private val context: Context,
    private val db: AppDatabase
) {
    data class ImportResult(
        val projects: Int,
        val spaces: Int,
        val openings: Int,
        val evidencePhotos: Int,
        val referencePhotos: Int
    )

    companion object {
        private const val FORMAT = "RELEVAMETAL_PROJECT_ARCHIVE"
        private const val FORMAT_VERSION = 1
        private const val MANIFEST = "manifest.json"
    }

    suspend fun export(projectIds: List<Long>): File {
        require(projectIds.isNotEmpty()) { "No hay obras seleccionadas para exportar" }

        val uniqueIds = projectIds.distinct()
        val media = mutableListOf<Pair<String, File>>()
        val projectsJson = JSONArray()

        uniqueIds.forEach { projectId ->
            val project = db.projects().project(projectId) ?: error("No se encontró una de las obras seleccionadas")
            val projectJson = projectToJson(project)

            val referenceJson = JSONArray()
            db.projectReferencePhotos().list(projectId).forEach { photo ->
                val source = requireMedia(photo.filePath, "una foto general de ${project.name}")
                val archivePath = "media/projects/${project.id}/reference/${photo.id}_${safeFileName(source.name)}"
                media += archivePath to source
                referenceJson.put(
                    JSONObject()
                        .put("id", photo.id)
                        .put("createdAt", photo.createdAt)
                        .put("file", archivePath)
                )
            }
            projectJson.put("referencePhotos", referenceJson)

            val spacesJson = JSONArray()
            db.spaces().list(projectId).forEach { space ->
                val spaceJson = spaceToJson(space)
                val openingsJson = JSONArray()
                db.openings().list(space.id).forEach { opening ->
                    val openingJson = openingToJson(opening)
                    val evidenceJson = JSONArray()
                    db.evidence().list(opening.id).forEach { evidence ->
                        val source = requireMedia(evidence.filePath, "una fotografía del vano ${opening.code}")
                        val archivePath = "media/projects/${project.id}/evidence/${evidence.id}_${safeFileName(source.name)}"
                        media += archivePath to source
                        evidenceJson.put(evidenceToJson(evidence).put("file", archivePath))
                    }
                    openingJson.put("evidence", evidenceJson)
                    openingsJson.put(openingJson)
                }
                spaceJson.put("openings", openingsJson)
                spacesJson.put(spaceJson)
            }
            projectJson.put("spaces", spacesJson)

            val eventsJson = JSONArray()
            db.events().list(projectId).forEach { event -> eventsJson.put(eventToJson(event)) }
            projectJson.put("events", eventsJson)
            projectsJson.put(projectJson)
        }

        val manifest = JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", System.currentTimeMillis())
            .put("archiveId", UUID.randomUUID().toString())
            .put("projectCount", projectsJson.length())
            .put("projects", projectsJson)

        val outDir = File(context.filesDir, "data_exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val label = if (uniqueIds.size == 1) {
            val projectName = db.projects().project(uniqueIds.first())?.name.orEmpty()
            "_${safeStem(projectName).ifBlank { "obra" }}"
        } else {
            "_todas_las_obras"
        }
        val output = File(outDir, "RelevaMetal${label}_$stamp.gidea")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(output))).use { zip ->
            // JPEG/HEIC files are already compressed; BEST_SPEED avoids wasting CPU on them.
            zip.setLevel(Deflater.BEST_SPEED)
            zip.putNextEntry(ZipEntry(MANIFEST))
            zip.write(manifest.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val seen = mutableSetOf<String>()
            media.forEach { (archivePath, source) ->
                require(seen.add(archivePath)) { "El archivo de transferencia contiene una ruta duplicada" }
                zip.putNextEntry(ZipEntry(archivePath))
                BufferedInputStream(FileInputStream(source)).use { input -> input.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return output
    }

    suspend fun import(uri: Uri): ImportResult {
        val staging = File(context.cacheDir, "relevametal_import_${UUID.randomUUID()}").apply { mkdirs() }
        val copiedFiles = mutableListOf<File>()
        try {
            extractSafely(uri, staging)
            val manifestFile = File(staging, MANIFEST)
            require(manifestFile.isFile) { "El archivo no contiene un manifiesto de RelevaMetal" }
            val root = JSONObject(manifestFile.readText(Charsets.UTF_8))
            require(root.optString("format") == FORMAT) { "El archivo seleccionado no es un respaldo válido de RelevaMetal" }
            val version = root.optInt("formatVersion", -1)
            require(version in 1..FORMAT_VERSION) {
                "Este respaldo usa una versión de datos más nueva. Actualizá la aplicación antes de importarlo."
            }
            val projects = root.optJSONArray("projects") ?: error("El respaldo no contiene obras")
            require(projects.length() > 0) { "El respaldo no contiene obras" }

            var spacesCount = 0
            var openingsCount = 0
            var evidenceCount = 0
            var referenceCount = 0

            db.withTransaction {
                for (pi in 0 until projects.length()) {
                    val projectJson = projects.getJSONObject(pi)
                    val oldProjectId = projectJson.getLong("id")
                    val newProjectId = db.projects().insert(projectFromJson(projectJson).copy(id = 0))

                    val openingIdMap = mutableMapOf<Long, Long>()

                    val references = projectJson.optJSONArray("referencePhotos") ?: JSONArray()
                    for (ri in 0 until references.length()) {
                        val item = references.getJSONObject(ri)
                        val source = archiveMedia(staging, item.getString("file"))
                        val target = copyImportedImage(
                            source = source,
                            targetDir = File(context.filesDir, "project_reference_photos/$newProjectId"),
                            prefix = "import_ref"
                        )
                        copiedFiles += target
                        db.projectReferencePhotos().insert(
                            ProjectReferencePhotoEntity(
                                projectId = newProjectId,
                                filePath = target.absolutePath,
                                createdAt = item.optLong("createdAt", System.currentTimeMillis())
                            )
                        )
                        referenceCount++
                    }

                    val spaces = projectJson.optJSONArray("spaces") ?: JSONArray()
                    for (si in 0 until spaces.length()) {
                        val spaceJson = spaces.getJSONObject(si)
                        val newSpaceId = db.spaces().insert(
                            spaceFromJson(spaceJson, newProjectId).copy(id = 0)
                        )
                        spacesCount++

                        val openings = spaceJson.optJSONArray("openings") ?: JSONArray()
                        for (oi in 0 until openings.length()) {
                            val openingJson = openings.getJSONObject(oi)
                            val oldOpeningId = openingJson.getLong("id")
                            val newOpeningId = db.openings().insert(
                                openingFromJson(openingJson, newSpaceId).copy(id = 0)
                            )
                            openingIdMap[oldOpeningId] = newOpeningId
                            openingsCount++

                            val evidence = openingJson.optJSONArray("evidence") ?: JSONArray()
                            for (ei in 0 until evidence.length()) {
                                val evidenceJson = evidence.getJSONObject(ei)
                                val source = archiveMedia(staging, evidenceJson.getString("file"))
                                val target = copyImportedImage(
                                    source = source,
                                    targetDir = File(context.filesDir, "evidence"),
                                    prefix = "import_ev"
                                )
                                copiedFiles += target
                                db.evidence().insert(
                                    evidenceFromJson(evidenceJson, newOpeningId, target.absolutePath).copy(id = 0)
                                )
                                evidenceCount++
                            }
                        }
                    }

                    val events = projectJson.optJSONArray("events") ?: JSONArray()
                    for (ei in 0 until events.length()) {
                        val eventJson = events.getJSONObject(ei)
                        val oldOpeningId = eventJson.optLongOrNull("openingId")
                        db.events().insert(
                            eventFromJson(
                                json = eventJson,
                                newProjectId = newProjectId,
                                newOpeningId = oldOpeningId?.let(openingIdMap::get)
                            ).copy(id = 0)
                        )
                    }

                    // Sanity check: the nested project id should agree with the exported object.
                    require(oldProjectId > 0) { "El respaldo contiene un identificador de obra inválido" }
                }
            }

            return ImportResult(
                projects = projects.length(),
                spaces = spacesCount,
                openings = openingsCount,
                evidencePhotos = evidenceCount,
                referencePhotos = referenceCount
            )
        } catch (t: Throwable) {
            copiedFiles.forEach { runCatching { it.delete() } }
            throw t
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun extractSafely(uri: Uri, targetDir: File) {
        val rootPath = targetDir.canonicalPath + File.separator
        val input = context.contentResolver.openInputStream(uri)
            ?: error("No se pudo abrir el archivo seleccionado")
        ZipInputStream(BufferedInputStream(input)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val normalized = entry.name.replace('\\', '/')
                require(normalized.isNotBlank() && !normalized.startsWith("/")) { "El respaldo contiene una ruta inválida" }
                val target = File(targetDir, normalized)
                require(target.canonicalPath.startsWith(rootPath)) { "El respaldo contiene una ruta no permitida" }
                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    BufferedOutputStream(FileOutputStream(target)).use { output -> zip.copyTo(output) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }

    private fun archiveMedia(staging: File, relativePath: String): File {
        val rootPath = staging.canonicalPath + File.separator
        val target = File(staging, relativePath)
        require(target.canonicalPath.startsWith(rootPath) && target.isFile) {
            "Falta una fotografía dentro del respaldo"
        }
        return target
    }

    private fun copyImportedImage(source: File, targetDir: File, prefix: String): File {
        targetDir.mkdirs()
        val ext = source.extension.lowercase(Locale.US)
            .filter { it.isLetterOrDigit() }
            .take(8)
            .ifBlank { "jpg" }
        val target = File(targetDir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.$ext")
        source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(target.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            target.delete()
            error("El respaldo contiene una fotografía dañada o no compatible")
        }
        return target
    }

    private fun requireMedia(path: String, description: String): File {
        val file = File(path)
        require(file.isFile && file.length() > 0L) { "No se pudo incluir $description porque el archivo original no está disponible" }
        return file
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").take(120).ifBlank { "imagen.jpg" }

    private fun safeStem(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9ÁÉÍÓÚáéíóúÑñ_-]+"), "_").trim('_').take(60)

    private fun projectToJson(item: ProjectEntity) = JSONObject()
        .put("id", item.id)
        .put("name", item.name)
        .put("client", item.client)
        .put("address", item.address)
        .put("createdAt", item.createdAt)
        .put("updatedAt", item.updatedAt)
        .put("notes", item.notes)
        .put("status", item.status)
        .put("responsible", item.responsible)

    private fun projectFromJson(json: JSONObject) = ProjectEntity(
        id = json.getLong("id"),
        name = json.optString("name"),
        client = json.optString("client"),
        address = json.optString("address"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
        notes = json.optString("notes"),
        status = json.optString("status", "EN_CURSO"),
        responsible = json.optString("responsible")
    )

    private fun spaceToJson(item: SpaceEntity) = JSONObject()
        .put("id", item.id)
        .put("projectId", item.projectId)
        .put("name", item.name)
        .put("level", item.level)
        .put("sector", item.sector)
        .put("notes", item.notes)
        .put("createdAt", item.createdAt)
        .put("updatedAt", item.updatedAt)

    private fun spaceFromJson(json: JSONObject, newProjectId: Long) = SpaceEntity(
        id = json.getLong("id"),
        projectId = newProjectId,
        name = json.optString("name"),
        level = json.optString("level"),
        sector = json.optString("sector"),
        notes = json.optString("notes"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", 0L)
    )

    private fun openingToJson(item: OpeningEntity) = JSONObject()
        .put("id", item.id)
        .put("spaceId", item.spaceId)
        .put("code", item.code)
        .put("type", item.type)
        .putNullable("widthMm", item.widthMm)
        .putNullable("heightMm", item.heightMm)
        .putNullable("sillMm", item.sillMm)
        .putNullable("diagonal1Mm", item.diagonal1Mm)
        .putNullable("diagonal2Mm", item.diagonal2Mm)
        .putNullable("wallThicknessMm", item.wallThicknessMm)
        .putNullable("depthMm", item.depthMm)
        .putNullable("clearanceLeftMm", item.clearanceLeftMm)
        .putNullable("clearanceRightMm", item.clearanceRightMm)
        .putNullable("clearanceTopMm", item.clearanceTopMm)
        .putNullable("clearanceBottomMm", item.clearanceBottomMm)
        .put("plumbState", item.plumbState)
        .put("levelState", item.levelState)
        .put("squareState", item.squareState)
        .put("floorState", item.floorState)
        .put("plasterState", item.plasterState)
        .put("premarcoState", item.premarcoState)
        .put("openingDirection", item.openingDirection)
        .put("interference", item.interference)
        .put("status", item.status)
        .put("notes", item.notes)
        .put("createdAt", item.createdAt)
        .put("updatedAt", item.updatedAt)

    private fun openingFromJson(json: JSONObject, newSpaceId: Long) = OpeningEntity(
        id = json.getLong("id"),
        spaceId = newSpaceId,
        code = json.optString("code"),
        type = json.optString("type", "VANO"),
        widthMm = json.optIntOrNull("widthMm"),
        heightMm = json.optIntOrNull("heightMm"),
        sillMm = json.optIntOrNull("sillMm"),
        diagonal1Mm = json.optIntOrNull("diagonal1Mm"),
        diagonal2Mm = json.optIntOrNull("diagonal2Mm"),
        wallThicknessMm = json.optIntOrNull("wallThicknessMm"),
        depthMm = json.optIntOrNull("depthMm"),
        clearanceLeftMm = json.optIntOrNull("clearanceLeftMm"),
        clearanceRightMm = json.optIntOrNull("clearanceRightMm"),
        clearanceTopMm = json.optIntOrNull("clearanceTopMm"),
        clearanceBottomMm = json.optIntOrNull("clearanceBottomMm"),
        plumbState = json.optString("plumbState", "NO_VERIFICADO"),
        levelState = json.optString("levelState", "NO_VERIFICADO"),
        squareState = json.optString("squareState", "NO_VERIFICADO"),
        floorState = json.optString("floorState", "NO_VERIFICADO"),
        plasterState = json.optString("plasterState", "NO_VERIFICADO"),
        premarcoState = json.optString("premarcoState", "NO_VERIFICADO"),
        openingDirection = json.optString("openingDirection"),
        interference = json.optString("interference"),
        status = json.optString("status", "PENDIENTE"),
        notes = json.optString("notes"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
    )

    private fun evidenceToJson(item: EvidenceEntity) = JSONObject()
        .put("id", item.id)
        .put("openingId", item.openingId)
        .put("caption", item.caption)
        .put("createdAt", item.createdAt)
        .put("detectedJson", item.detectedJson)
        .put("annotationJson", item.annotationJson)
        .put("updatedAt", item.updatedAt)
        .put("isPrimary", item.isPrimary)
        .put("rotationDegrees", item.rotationDegrees)
        .put("phase", item.phase)

    private fun evidenceFromJson(json: JSONObject, newOpeningId: Long, newPath: String) = EvidenceEntity(
        id = json.getLong("id"),
        openingId = newOpeningId,
        filePath = newPath,
        caption = json.optString("caption"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        detectedJson = json.optString("detectedJson"),
        annotationJson = json.optString("annotationJson"),
        updatedAt = json.optLong("updatedAt", 0L),
        isPrimary = json.optBoolean("isPrimary", false),
        rotationDegrees = json.optInt("rotationDegrees", 0),
        phase = json.optString("phase", "GENERAL")
    )

    private fun eventToJson(item: EventEntity) = JSONObject()
        .put("id", item.id)
        .put("projectId", item.projectId)
        .put("kind", item.kind)
        .put("title", item.title)
        .put("detail", item.detail)
        .put("createdAt", item.createdAt)
        .putNullable("openingId", item.openingId)
        .put("severity", item.severity)

    private fun eventFromJson(json: JSONObject, newProjectId: Long, newOpeningId: Long?) = EventEntity(
        id = json.getLong("id"),
        projectId = newProjectId,
        kind = json.optString("kind"),
        title = json.optString("title"),
        detail = json.optString("detail"),
        createdAt = json.optLong("createdAt", System.currentTimeMillis()),
        openingId = newOpeningId,
        severity = json.optString("severity", "INFO")
    )

    private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
        put(name, value ?: JSONObject.NULL)

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (!has(name) || isNull(name)) null else optInt(name)

    private fun JSONObject.optLongOrNull(name: String): Long? =
        if (!has(name) || isNull(name)) null else optLong(name)
}
