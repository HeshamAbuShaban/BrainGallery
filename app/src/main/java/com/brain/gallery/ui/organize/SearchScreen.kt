package com.brain.gallery.ui.organize

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.brain.gallery.ui.components.VideoThumb
import com.brain.gallery.ui.theme.Bg
import com.brain.gallery.ui.theme.Surface
import com.brain.gallery.ui.theme.Text1
import com.brain.gallery.ui.theme.Text2

@Composable
fun SearchScreen(vm: SearchViewModel = hiltViewModel()) {
    val q by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    Column(Modifier.fillMaxSize().background(Bg).padding(top = 20.dp)) {
        Text("Search", color = Text1, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 20.dp))
        Text("Names, tags, categories, folders", color = Text2, fontSize = 12.5.sp,
            modifier = Modifier.padding(horizontal = 20.dp))
        TextField(value = q, onValueChange = { vm.query(it) },
            placeholder = { Text("birthday, beach, concert…", color = Text2) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Text2) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Surface, unfocusedContainerColor = Surface,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                focusedTextColor = Text1, unfocusedTextColor = Text1),
            modifier = Modifier.fillMaxWidth().padding(20.dp, 14.dp, 20.dp, 4.dp))
        if (q.length >= 2 && results.isEmpty()) {
            Text("No matches for \"$q\"", color = Text2, fontSize = 13.sp,
                modifier = Modifier.padding(20.dp))
        }
        LazyVerticalGrid(GridCells.Fixed(3),
            contentPadding = PaddingValues(20.dp, 8.dp, 20.dp, 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(results, key = { it.id }) { v ->
                VideoThumb(v, Modifier.aspectRatio(0.7f))
            }
        }
    }
}
