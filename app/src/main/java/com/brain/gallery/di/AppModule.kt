package com.brain.gallery.di

import android.content.Context
import androidx.room.Room
import com.brain.gallery.data.local.BrainDatabase
import com.brain.gallery.data.local.MIGRATION_4_5
import com.brain.gallery.data.local.MIGRATION_5_6
import com.brain.gallery.data.local.MIGRATION_6_7
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
            .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
            .build()

    @Provides fun videoDao(db: BrainDatabase) = db.videoDao()
    @Provides fun watchDao(db: BrainDatabase) = db.watchDao()
    @Provides fun personDao(db: BrainDatabase) = db.personDao()
    @Provides fun supportDao(db: BrainDatabase) = db.supportDao()
}
