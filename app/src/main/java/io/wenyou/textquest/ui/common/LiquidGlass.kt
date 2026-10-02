package io.wenyou.textquest.ui.common

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

private class Backdrop(val layer: GraphicsLayer) {
    var origin by mutableStateOf(Offset.Zero)
}
private val LocalBackdrop = staticCompositionLocalOf<Backdrop?> { null }

/** Record only the content behind the controls; never record the glass itself. */
@Composable
fun GlassBackdrop(content: @Composable () -> Unit, controls: @Composable BoxScope.() -> Unit) {
    val layer = rememberGraphicsLayer()
    val backdrop = remember(layer) { Backdrop(layer) }
    CompositionLocalProvider(LocalBackdrop provides backdrop) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().onGloballyPositioned { backdrop.origin = it.positionInRoot() }
                .drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                }) { content() }
            controls()
        }
    }
}

/** Background sampling, blur and edge lensing are isolated from readable foreground text. */
@Composable
fun Modifier.liquidGlass(): Modifier {
    val backdrop = LocalBackdrop.current
    val sample = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val tint = MaterialTheme.colorScheme.surface
    val shader = remember {
        if (Build.VERSION.SDK_INT >= 33) RuntimeShader(GLASS_SHADER) else null
    }
    val shape = RoundedCornerShape(30.dp)
    return this.shadow(12.dp, shape, clip = false).clip(shape).onGloballyPositioned { origin = it.positionInRoot() }
        .drawWithContent {
            if (backdrop != null && Build.VERSION.SDK_INT >= 31) {
                val offset = origin - backdrop.origin
                sample.record {
                    drawRect(tint)
                    translate(-offset.x, -offset.y) { drawLayer(backdrop.layer) }
                }
                sample.renderEffect = if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                    shader.setFloatUniform("extent", size.width, size.height)
                    shader.setFloatUniform("radius", minOf(30.dp.toPx(), size.height / 2f))
                    shader.setFloatUniform("density", density)
                    RenderEffect.createChainEffect(
                        RenderEffect.createRuntimeShaderEffect(shader, "backdrop"),
                        RenderEffect.createBlurEffect(12.dp.toPx(), 12.dp.toPx(), Shader.TileMode.CLAMP)
                    ).asComposeRenderEffect()
                } else {
                    RenderEffect.createBlurEffect(12.dp.toPx(), 12.dp.toPx(), Shader.TileMode.CLAMP).asComposeRenderEffect()
                }
                drawLayer(sample)
                drawRect(tint.copy(alpha = if (dark) 0.58f else 0.68f))
            } else {
                // ponytail: Android 8–11 retain readable tinted glass; GPU backdrop effects need Android 12+.
                drawRect(tint.copy(alpha = 0.94f))
            }
            drawRect(Brush.linearGradient(listOf(Color.White.copy(alpha = if (dark) 0.14f else 0.32f), Color.Transparent)))
            drawRoundRect(
                Brush.linearGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0.10f), Color.White.copy(alpha = 0.38f))),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(30.dp.toPx()),
                style = Stroke(1.dp.toPx())
            )
            drawContent()
        }
}

// Original AGSL: rounded-rectangle normal drives the edge lens over a Gaussian-blurred backdrop.
internal const val GLASS_SHADER = """
uniform shader backdrop;
uniform float2 extent;
uniform float radius;
uniform float density;
float distanceToEdge(float2 p) {
    float2 q = abs(p - extent * 0.5) - (extent * 0.5 - radius);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}
half4 main(float2 p) {
    float d = distanceToEdge(p);
    float e = max(density, 1.0);
    float2 gradient = float2(distanceToEdge(p + float2(e, 0)) - distanceToEdge(p - float2(e, 0)),
                              distanceToEdge(p + float2(0, e)) - distanceToEdge(p - float2(0, e)));
    float2 normal = gradient / max(length(gradient), 0.001);
    float rim = exp(-abs(d) / (7.0 * density));
    float2 uv = clamp(p - normal * rim * 8.0 * density, float2(0.5), extent - 0.5);
    half4 c = backdrop.eval(uv);
    float glint = pow(max(dot(normal, normalize(float2(-1, -1))), 0.0), 3.0) * rim * 0.22;
    return half4(min(c.rgb + half3(glint), half3(1.0)), c.a);
}
"""
