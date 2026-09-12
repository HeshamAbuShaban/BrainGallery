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
    val JUNK_FOLDERS = setOf("whatsapp", "telegram", "download", "downloads", "screenshots", "screenshot", "screenrecordings", "instagram", "tiktok", "snapchat", "memes")
    val MEMORY_FOLDERS = setOf("camera", "dcim", "photos", "videos")

    fun matchFile(name: String): String? {
        val lower = name.lowercase()
        for ((cat, keys) in MAP) if (keys.any { lower.contains(it) }) return cat
        return null
    }

    fun junkScore(folder: String, name: String): Float {
        val f = folder.lowercase()
        val n = name.lowercase()
        var s = 0f
        if (f in JUNK_FOLDERS) s += 0.6f
        if (f.contains("screenshot") || n.contains("screenshot") || n.contains("screenrec")) s += 0.4f
        if (f in MEMORY_FOLDERS) s -= 0.5f
        if (n.startsWith("vid_") || n.startsWith("img_") || n.startsWith("video")) s -= 0.1f
        return s.coerceIn(0f, 1f)
    }
}
