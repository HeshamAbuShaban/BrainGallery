package com.brain.gallery.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

@Entity(tableName = "watch_events")
data class WatchEventEntity(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,
    val videoId: Long,
    val atMs: Long,
    val completion: Float, // 0..1 watched fraction
    val skipped: Boolean, // left before 15%
    val hourOfDay: Int
)

@Dao
interface WatchEventDao {
    @Insert suspend fun insert(e: WatchEventEntity)
    @Query("SELECT * FROM watch_events ORDER BY atMs DESC LIMIT 300")
    suspend fun recent(): List<WatchEventEntity>
}
