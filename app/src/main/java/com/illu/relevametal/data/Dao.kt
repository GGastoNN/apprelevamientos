package com.illu.relevametal.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") fun observeProjects(): Flow<List<ProjectEntity>>
    @Query("SELECT * FROM projects WHERE id=:id") suspend fun project(id: Long): ProjectEntity?
    @Insert suspend fun insert(project: ProjectEntity): Long
    @Update suspend fun update(project: ProjectEntity)
    @Delete suspend fun delete(project: ProjectEntity)
}

@Dao
interface SpaceDao {
    @Query("SELECT * FROM spaces WHERE projectId=:projectId ORDER BY createdAt") fun observe(projectId: Long): Flow<List<SpaceEntity>>
    @Query("SELECT * FROM spaces WHERE projectId=:projectId ORDER BY createdAt") suspend fun list(projectId: Long): List<SpaceEntity>
    @Insert suspend fun insert(item: SpaceEntity): Long
}

@Dao
interface OpeningDao {
    @Query("SELECT * FROM openings WHERE spaceId=:spaceId ORDER BY code") fun observe(spaceId: Long): Flow<List<OpeningEntity>>
    @Query("SELECT * FROM openings WHERE spaceId=:spaceId ORDER BY code") suspend fun list(spaceId: Long): List<OpeningEntity>
    @Query("SELECT * FROM openings WHERE id=:id") suspend fun get(id: Long): OpeningEntity?
    @Insert suspend fun insert(item: OpeningEntity): Long
    @Update suspend fun update(item: OpeningEntity)
}

@Dao
interface EvidenceDao {
    @Query("SELECT * FROM evidence WHERE openingId=:openingId ORDER BY createdAt") fun observe(openingId: Long): Flow<List<EvidenceEntity>>
    @Query("SELECT * FROM evidence WHERE openingId=:openingId ORDER BY createdAt") suspend fun list(openingId: Long): List<EvidenceEntity>
    @Insert suspend fun insert(item: EvidenceEntity): Long
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE projectId=:projectId ORDER BY createdAt DESC") fun observe(projectId: Long): Flow<List<EventEntity>>
    @Insert suspend fun insert(item: EventEntity): Long
}
