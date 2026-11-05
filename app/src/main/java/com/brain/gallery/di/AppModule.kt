package com.brain.gallery.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.MIGRATION_4_5
import com.brain.gallery.data.local.MIGRATION_5_6
import com.brain.gallery.data.local.MIGRATION_6_7
import com.brain.gallery.data.local.MIGRATION_7_8
import com.brain.gallery.data.local.MIGRATION_8_9
import com.brain.gallery.data.local.MIGRATION_9_10
import com.brain.gallery.data.local.MIGRATION_10_11
import com.brain.gallery.data.local.MIGRATION_11_12
import com.brain.gallery.data.search.FtsSchema
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context): BrainDatabase =
        Room.databaseBuilder(ctx, BrainDatabase::class.java, "brain_gallery.db")
            .addMigrations(
                MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11,
                MIGRATION_11_12)
            // Fresh installs: Room builds its own tables; the FTS search index
            // is not one of them, so it is created (and filled) right here.
            .addCallback(object : RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(FtsSchema.VIDEO_FTS_DDL)
                    db.execSQL(FtsSchema.PHOTO_FTS_DDL)
                    db.execSQL(FtsSchema.VIDEO_FILL)
                    db.execSQL(FtsSchema.PHOTO_FILL)
                }
            })
            .build()

    @Provides fun videoDao(db: BrainDatabase) = db.videoDao()
    @Provides fun watchDao(db: BrainDatabase) = db.watchDao()
    @Provides fun personDao(db: BrainDatabase) = db.personDao()
    @Provides fun supportDao(db: BrainDatabase) = db.supportDao()
    @Provides fun photoDao(db: BrainDatabase) = db.photoDao()
    @Provides fun searchFtsDao(db: BrainDatabase) = db.searchFtsDao()

    /**
     * The voice layer's words: the phone's own on-device recogniser, or an
     * honest null when the phone has none. No permissions asked, nothing
     * downloaded, nothing sent anywhere.
     */
    @Provides @Singleton
    fun transcriber(@ApplicationContext ctx: Context): com.brain.gallery.data.brain.Transcriber =
        com.brain.gallery.data.brain.OnDeviceTranscriber(ctx)
}
