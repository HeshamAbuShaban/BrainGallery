package com.brain.gallery.ui.glass
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
enum class GlassFlavor(val label: String, val tint: Color) {
    FROST("Apple Frost", Color(0xFF6B85A6)),
    SUNSET("Apple Sunset", Color(0xFFE8B874)),
    MIDNIGHT("Apple Midnight", Color(0xFF5A9BD5)),
    PURE_WHITE("Apple Glass", Color(0xFF8E9AAF))
}
