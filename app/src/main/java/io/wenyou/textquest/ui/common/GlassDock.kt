package io.wenyou.textquest.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import io.wenyou.textquest.ui.common.AppIcon as Icon
import io.wenyou.textquest.ui.common.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.wenyou.textquest.ui.theme.*
import kotlin.math.roundToInt

data class DockItem(val label: String, val icon: ImageVector)

/** Whole-slot lens and outline glyphs follow the reference dock's visual proportions. */
@Composable
fun GlassDock(items: List<DockItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val prefs = LocalAppearance.current
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(1f) }
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val latestSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val position = animateFloatAsState(selected.toFloat(), spring(dampingRatio = .82f, stiffness = 550f), label = "dock-lens")
    BoxWithConstraints(modifier.fillMaxWidth().heightIn(min = 64.dp).onSizeChanged { widthPx = it.width.toFloat() }
        .pointerInput(items.size) {
            detectHorizontalDragGestures(
                onDragStart = { offset -> dragPosition = (offset.x / widthPx * items.size - .5f).coerceIn(0f, items.lastIndex.toFloat()) },
                onHorizontalDrag = { change, amount -> change.consume(); dragPosition = ((dragPosition ?: currentSelected.toFloat()) + amount / widthPx * items.size).coerceIn(0f, items.lastIndex.toFloat()) },
                onDragCancel = { dragPosition = null },
                onDragEnd = { dragPosition?.roundToInt()?.let(latestSelect); dragPosition = null }
            )
        }) {
        val slot = maxWidth / items.size
        val indicatorColor = distributedAccent(selected + 2, MaterialTheme.colorScheme.onSurface.copy(alpha = .08f))
        Box(Modifier.padding(4.dp).width((slot - 8.dp).coerceAtLeast(1.dp)).height(56.dp)
            .graphicsLayer { translationX = (dragPosition ?: position.value) * with(density) { slot.toPx() }; scaleX = if (dragPosition == null) 1f else 1.04f }
            .background(indicatorColor, RoundedCornerShape(50)))
        Row(Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            items.forEachIndexed { index, item ->
                val accent = distributedAccent(index + 2, MaterialTheme.colorScheme.readableAccent())
                val tint = if (index == selected && LocalAccentPalette.current.isNotEmpty()) accentForeground(indicatorColor) else if (index == selected) {
                    if (androidx.core.graphics.ColorUtils.calculateContrast(accent.toArgb(), MaterialTheme.colorScheme.surface.toArgb()) >= 4.5) accent else MaterialTheme.colorScheme.onSurface
                } else MaterialTheme.colorScheme.onSurface
                Column(Modifier.weight(1f).heightIn(min = 64.dp)
                    .selectable(index == selected, role = Role.Tab, onClick = { latestSelect(index) })
                    .padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    if (prefs.dockLabels != "text") Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(26.dp))
                    if (prefs.dockLabels != "icons") {
                        if (prefs.dockLabels == "both") Spacer(Modifier.height(2.dp))
                        Text(item.label, color = tint, fontSize = ((if (prefs.dockLabels == "text") 15f else 11f) * density.fontScale.coerceAtMost(1.15f) / density.fontScale).sp,
                            lineHeight = (14f * density.fontScale.coerceAtMost(1.15f) / density.fontScale).sp,
                            fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
    }
}
