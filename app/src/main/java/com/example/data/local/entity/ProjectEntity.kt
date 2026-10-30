package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity representing a saved PBR texture generation project.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val resolution: Int = 2048,
    val thumbnailPath: String? = null,
    val mapsCount: Int = 8,
    val isFavorite: Boolean = false,
    val generationMode: String = "AI_DEPTH",
    val isSeamless: Boolean = false
)
