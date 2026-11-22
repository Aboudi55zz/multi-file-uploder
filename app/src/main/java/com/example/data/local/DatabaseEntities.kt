package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "upload_history")
data class UploadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val folderName: String,
    val destinationType: String,
    val targetLabel: String,
    val fileCount: Int,
    val totalBytes: Long,
    val status: String,
    val resultLink: String?,
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_targets")
data class SavedTargetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val destinationType: String,
    val configJson: String,
    val tokenKey: String,
    val createdAt: Long = System.currentTimeMillis()
)
