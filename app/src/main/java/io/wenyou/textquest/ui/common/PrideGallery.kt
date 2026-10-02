package io.wenyou.textquest.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.wenyou.textquest.ui.theme.PrideTheme

val LocalPrideTagClick = staticCompositionLocalOf<() -> Unit> { {} }

@Composable
internal fun PrideFlag(theme: PrideTheme, modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(5f / 3f).clip(RoundedCornerShape(4.dp))
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
        .semantics { contentDescription = "${theme.label}旗帜" }) {
        if (theme == PrideTheme.INTERSEX) {
            drawRect(theme.colors[0])
            drawCircle(theme.colors[1], size.height * 0.28f, style = Stroke(size.height * 0.075f))
        } else {
            val weights = theme.stripeWeights
            var y = 0f
            theme.colors.forEachIndexed { index, color ->
                val height = size.height * weights[index] / weights.sum()
                drawRect(color, Offset(0f, y), Size(size.width, height + 0.5f))
                y += height
            }
            if (theme == PrideTheme.DEMISEXUAL) drawPath(Path().apply {
                moveTo(0f, 0f); lineTo(size.width * 0.35f, size.height / 2f); lineTo(0f, size.height); close()
            }, Color.Black)
        }
    }
}

@Composable
internal fun PrideGallery(taps: Int, selected: PrideTheme?, onTap: () -> Unit, onSelect: (PrideTheme) -> Unit, onDismiss: () -> Unit) {
    val unlocked = taps >= 15
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text("骄傲旗帜馆") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = onTap, label = { Text("LGBT") }, modifier = Modifier.testTag("pride-gallery-tap"))
                Text(if (unlocked) "已解锁 ${PrideTheme.entries.size} 款旗帜配色，可立即应用或在设置中切换。"
                     else "再点击上方 LGBT 标签 ${15 - taps} 次，解锁全部旗帜配色。")
                LazyColumn(Modifier.weight(1f, fill = false).heightIn(max = 420.dp).testTag("pride-flags"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(PrideTheme.entries, key = { it.name }) { theme ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PrideFlag(theme, Modifier.width(90.dp))
                            Column(Modifier.weight(1f)) {
                                Text(theme.label, style = MaterialTheme.typography.titleSmall)
                                if (unlocked) TextButton(onClick = { onSelect(theme) }) {
                                    Text(if (selected == theme) "已应用" else "应用配色")
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭旗帜馆") } })
}
