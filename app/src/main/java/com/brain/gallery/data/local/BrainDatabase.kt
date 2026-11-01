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
        SelectionEntity::class,
        AlbumEntity::class,
        PhotoEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class BrainDatabase : RoomDatabase() {
    abstract fun videoDao(): VideoDao
    abstract fun watchDao(): WatchEventDao
    abstract fun personDao(): PersonDao
    abstract fun supportDao(): SupportDao

    abstract fun photoDao(): PhotoDao
}
