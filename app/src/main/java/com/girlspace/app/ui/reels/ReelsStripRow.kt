package com.girlspace.app.ui.reels
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.girlspace.app.data.reels.Reel

@Composable
fun ReelsStripRow(
    navController: NavHostController
) {
    val vm: ReelsStripViewModel = hiltViewModel()
    val reels by vm.reels.collectAsState()

    LaunchedEffect(Unit) { vm.load() }
    if (reels.isEmpty()) return

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(reels, key = { _, r -> r.id }) { idx, r ->
            val size = when (idx % 3) {
                0 -> ReelCardSize.SMALL
                1 -> ReelCardSize.MEDIUM
                else -> ReelCardSize.LARGE
            }
            ReelPreviewCard(reel = r, size = size) {
                navController.navigate("reelsViewer/${r.id}")
            }
        }
    }
}

private enum class ReelCardSize(val w: Dp, val h: Dp) {
    SMALL(110.dp, 170.dp),
    MEDIUM(140.dp, 210.dp),
    LARGE(180.dp, 250.dp)
}

@Composable
private fun ReelPreviewCard(reel: Reel, size: ReelCardSize, onClick: () -> Unit) {
    Card(
        modifier = Modifier.size(size.w, size.h).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            AsyncImage(
                model = reel.thumbnailUrl,
                contentDescription = "Reel",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.align(androidx.compose.ui.Alignment.BottomStart).padding(10.dp)) {
                Text(
                    text = reel.caption.ifBlank { "Reel" },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        }
    }
}
