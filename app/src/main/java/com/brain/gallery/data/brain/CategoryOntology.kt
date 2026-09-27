package com.brain.gallery.data.brain

object CategoryOntology {
    val MAP: Map<String, List<String>> = mapOf(
        "birthday" to listOf("birthday", "bday", "cake", "party"),
        "trip" to listOf("trip", "travel", "beach", "vacation", "tour", "holiday"),
        "wedding" to listOf("wedding", "bride", "marriage"),
        "baby" to listOf("baby", "kid", "son", "daughter"),
        "pet" to listOf("cat", "dog", "pet", "puppy", "kitten"),
        "food" to listOf("food", "recipe", "cook", "restaurant", "eat"),
        "sport" to listOf("match", "goal", "sport", "football", "gym", "workout"),
        "music" to listOf("concert", "music", "song", "guitar", "piano", "sing"),
        "gaming" to listOf("game", "gameplay", "fortnite", "minecraft", "pubg"),
        "tutorial" to listOf("tutorial", "howto", "how_to", "lesson", "course"),
        "comedy" to listOf("funny", "meme", "comedy", "laugh", "prank"),
        "vlog" to listOf("vlog", "daily", "blog"),
        "movie" to listOf("movie", "film", "episode", "series", "netflix")
    )
    // Matched as substrings, not equality: real folders are named "TikTok-Vids",
    // "Screen recordings", "WhatsApp Video", and exact matching missed all of them.
    val JUNK_TOKENS = listOf(
        "whatsapp", "telegram", "instagram", "tiktok", "snapchat", "twitter", "facebook",
        "download", "screenrecord", "screen_record", "screenshot", "meme", "reels", "shorts",
        "ringtone", "cache", "temp", "trashed", "blender", "recorder", "voice msg", "voicemsg"
    )
    val MEMORY_TOKENS = listOf("camera", "dcim", "pictures", "camera roll", "camera_roll")
    val JUNK_NAME_TOKENS = listOf(
        "screenrecording", "screen_record", "screenshot", "snap ", "vid_202", "screen 20"
    )

    fun matchFile(name: String): String? {
        val lower = name.lowercase()
        for ((cat, keys) in MAP) if (keys.any { lower.contains(it) }) return cat
        return null
    }

    fun junkScore(folder: String, name: String): Float {
        val f = folder.lowercase().trim()
        val n = name.lowercase()
        var s = 0f
        if (JUNK_TOKENS.any { f.contains(it) }) s += 0.75f
        if (JUNK_NAME_TOKENS.any { n.contains(it) }) s += 0.45f
        if (MEMORY_TOKENS.any { f.contains(it) }) s -= 0.55f
        // Camera-shaped filenames are almost always real captured moments.
        if (n.startsWith("vid_20") || n.startsWith("img_20") || n.startsWith("mmvideo")
            || n.startsWith("wxv") || n.startsWith("panora")) s -= 0.45f
        return s.coerceIn(0f, 1f)
    }
}
