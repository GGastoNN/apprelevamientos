package com.illu.relevametal.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id=:id")
    suspend fun project(id: Long): ProjectEntity?

    @Insert
    suspend fun insert(project: ProjectEntity): Long

    @Update
    suspend fun update(project: ProjectEntity)

    @Delete
    suspend fun delete(project: ProjectEntity)
}


@Dao
interface ProjectReferencePhotoDao {
    @Query("SELECT * FROM project_reference_photos WHERE projectId=:projectId ORDER BY createdAt")
    fun observe(projectId: Long): Flow<List<ProjectReferencePhotoEntity>>

    @Query("SELECT * FROM project_reference_photos WHERE projectId=:projectId ORDER BY createdAt")
    suspend fun list(projectId: Long): List<ProjectReferencePhotoEntity>

    @Insert
    suspend fun insert(item: ProjectReferencePhotoEntity): Long

    @Delete
    suspend fun delete(item: ProjectReferencePhotoEntity)
}

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces WHERE projectId=:projectId ORDER BY createdAt")
    fun observe(projectId: Long): Flow<List<SpaceEntity>>

    @Query("SELECT * FROM spaces WHERE projectId=:projectId ORDER BY createdAt")
    suspend fun list(projectId: Long): List<SpaceEntity>

    @Query("SELECT * FROM spaces WHERE id=:id")
    suspend fun get(id: Long): SpaceEntity?

    @Query("SELECT COUNT(*) FROM spaces WHERE projectId=:projectId")
    fun count(projectId: Long): Flow<Int>

    @Insert
    suspend fun insert(item: SpaceEntity): Long

    @Update
    suspend fun update(item: SpaceEntity)
}

@Dao
interface OpeningDao {
    @Query("SELECT * FROM openings WHERE spaceId=:spaceId ORDER BY code")
    fun observe(spaceId: Long): Flow<List<OpeningEntity>>

    @Query("SELECT * FROM openings WHERE spaceId=:spaceId ORDER BY code")
    suspend fun list(spaceId: Long): List<OpeningEntity>

    @Query("SELECT * FROM openings WHERE id=:id")
    suspend fun get(id: Long): OpeningEntity?

    @Query("SELECT COUNT(*) FROM openings INNER JOIN spaces ON openings.spaceId=spaces.id WHERE spaces.projectId=:projectId")
    fun countForProject(projectId: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM openings INNER JOIN spaces ON openings.spaceId=spaces.id WHERE spaces.projectId=:projectId AND openings.status NOT IN ('RELEVADO','APROBADO')")
    fun pendingForProject(projectId: Long): Flow<Int>

    @Insert
    suspend fun insert(item: OpeningEntity): Long

    @Update
    suspend fun update(item: OpeningEntity)
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence WHERE openingId=:openingId ORDER BY isPrimary DESC, createdAt DESC")
    fun observe(openingId: Long): Flow<List<EvidenceEntity>>

    @Query("SELECT * FROM evidence WHERE openingId=:openingId ORDER BY isPrimary DESC, createdAt")
    suspend fun list(openingId: Long): List<EvidenceEntity>

    @Query("SELECT * FROM evidence WHERE id=:id")
    suspend fun get(id: Long): EvidenceEntity?

    @Query("SELECT COUNT(*) FROM evidence INNER JOIN openings ON evidence.openingId=openings.id INNER JOIN spaces ON openings.spaceId=spaces.id WHERE spaces.projectId=:projectId")
    fun countForProject(projectId: Long): Flow<Int>

    @Insert
    suspend fun insert(item: EvidenceEntity): Long

    @Update
    suspend fun update(item: EvidenceEntity)

    @Delete
    suspend fun delete(item: EvidenceEntity)

    @Query("UPDATE evidence SET isPrimary=0 WHERE openingId=:openingId")
    suspend fun clearPrimary(openingId: Long)
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE projectId=:projectId ORDER BY createdAt DESC")
    fun observe(projectId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE projectId=:projectId ORDER BY createdAt ASC")
    suspend fun list(projectId: Long): List<EventEntity>

    @Insert
    suspend fun insert(item: EventEntity): Long
}
