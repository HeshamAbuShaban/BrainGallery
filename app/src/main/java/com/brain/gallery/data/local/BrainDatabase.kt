package com.brain.gallery.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [VideoEntity::class, WatchEventEntity::class], version = 3, exportSchema = false)
abstract class BrainDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun watchDao(): WatchEventDao
}
