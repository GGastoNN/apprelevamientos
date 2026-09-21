package com.illu.relevametal.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val client: String = "",
    val address: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "spaces",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")]
)
data class SpaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val name: String,
    val level: String = "",
    val sector: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "openings",
    foreignKeys = [ForeignKey(SpaceEntity::class, ["id"], ["spaceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("spaceId")]
)
data class OpeningEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spaceId: Long,
    val code: String,
    val type: String = "VANO",
    val widthMm: Int? = null,
    val heightMm: Int? = null,
    val sillMm: Int? = null,
    val diagonal1Mm: Int? = null,
    val diagonal2Mm: Int? = null,
    val status: String = "PENDIENTE",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "evidence",
    foreignKeys = [ForeignKey(OpeningEntity::class, ["id"], ["openingId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("openingId")]
)
data class EvidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val openingId: Long,
    val filePath: String,
    val caption: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val detectedJson: String = "",
    val annotationJson: String = ""
)

@Entity(
    tableName = "events",
    foreignKeys = [ForeignKey(ProjectEntity::class, ["id"], ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val kind: String,
    val title: String,
    val detail: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
