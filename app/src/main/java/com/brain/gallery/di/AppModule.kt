package com.brain.gallery.di

import android.content.Context
import androidx.room.Room
import com.brain.gallery.data.local.BrainDatabase
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
            .fallbackToDestructiveMigration().build()

    @Provides fun videoDao(db: BrainDatabase) = db.videoDao()
    @Provides fun watchDao(db: BrainDatabase) = db.watchDao()
}
