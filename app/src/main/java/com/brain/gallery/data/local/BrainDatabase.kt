package com.brain.gallery.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        VideoEntity::class,
        WatchEventEntity::class,
        PersonEntity::class,
        FaceVectorEntity::class,
        NotInterestedEntity::class,
        SemanticVectorEntity::class,
        AppKvEntity::class,
        GroupOverrideEntity::class,
        SelectionEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class BrainDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun watchDao(): WatchEventDao
    abstract fun personDao(): PersonDao
    abstract fun supportDao(): SupportDao
}
