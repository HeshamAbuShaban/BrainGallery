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

/**
 * v5 -> v6: hand-placed groups and multi-select.
 *
 * Adds a nullable-by-default column and one scratch table, so no existing row is
 * touched. Every video starts with manualGroup = '', which reads as "no manual
 * decision" and keeps engine grouping exactly as it was.
 */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN manualGroup TEXT NOT NULL DEFAULT ''")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `selection` (
                `videoId` INTEGER NOT NULL,
                `pickedAt` INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY(`videoId`)
            )
            """.trimIndent()
        )
    }
}

/**
 * v6 -> v7: the optional voice layer.
 *
 * Two columns with defaults, plus an index so the pass can find its queue
 * cheaply. Additive again: no existing row is rewritten, and clips the audio
 * pass has not touched read as "not measured" rather than "measured and empty".
 *
 * The index name is not free: Room names an index `index_<table>_<column>` and
 * then compares it against the one in [VideoEntity.indices] after migrating.
 * A name that differs, or an index the entity does not declare, fails the
 * check and rolls the whole migration back — which is how v7 first shipped
 * crashing on an unchanged database.
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE videos ADD COLUMN prosodyTags TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE videos ADD COLUMN speechRatio REAL NOT NULL DEFAULT 0")
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_videos_prosodyTags ON videos (prosodyTags)"
        )
    }
}

/**
 * v7 -> v8: the user's own shelves.
 *
 * One new table plus one column. Additive, like every migration here: albums
 * start empty, clips read as belonging to no album, and nothing existing is
 * rewritten. The engine never writes albumId — it is the one column that only
 * changes when the person holding the phone says so.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `albums` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("ALTER TABLE videos ADD COLUMN albumId INTEGER NOT NULL DEFAULT -1")
    }
}

/**
 * v8 -> v9: photos become first-class citizens.
 *
 * Their own table, on purpose — a photo is not a degenerate video. Rows are
 * rebuilt from MediaStore by the same mark-and-sweep the video side uses, so
 * the table starts empty and fills honestly. Additive, like every migration.
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS `photos` (
                `id` INTEGER NOT NULL PRIMARY KEY,
                `uri` TEXT NOT NULL,
                `displayName` TEXT NOT NULL,
                `folderName` TEXT NOT NULL,
                `dateAddedSec` INTEGER NOT NULL,
                `width` INTEGER NOT NULL,
                `height` INTEGER NOT NULL,
                `category` TEXT NOT NULL,
                `tags` TEXT NOT NULL,
                `about` TEXT NOT NULL,
                `confidence` REAL NOT NULL,
                `brainLevel` INTEGER NOT NULL,
                `junkScore` REAL NOT NULL,
                `faceCount` INTEGER NOT NULL,
                `smileCount` INTEGER NOT NULL,
                `isFavorite` INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_folderName ON photos (folderName)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_brainLevel ON photos (brainLevel)")
    }
}
