package com.brain.gallery.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v4 -> v5: give group names somewhere to live.
 *
 * Group titles were always recomputed from the engine's label, so renaming a
 * non-person group (a category, an event) wrote nowhere and appeared to do
 * nothing. This adds the table that holds a name the user chose. Purely
 * additive: no existing table is touched, so no row can be lost.
 *
 * The app deliberately no longer falls back to a destructive migration. A
 * missing migration should fail loudly, never quietly delete a library.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `group_overrides` (
                `groupId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `updatedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`groupId`)
            )
            """.trimIndent()
        )
    }
}
