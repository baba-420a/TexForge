package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Placeholder entity for Room database skeleton (Phase 0).
 * Will be populated and used for project history in Phase 9.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolution: Int = 2048,
    val thumbnailPath: String? = null
)
