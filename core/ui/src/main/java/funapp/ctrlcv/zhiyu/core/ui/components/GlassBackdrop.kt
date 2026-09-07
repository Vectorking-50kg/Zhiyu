package funapp.ctrlcv.zhiyu.core.ui.components

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * A live display-list source shared by a page and its glass overlays. This never reads pixels or
 * creates a bitmap. Use one source per state and keep every overlay outside that source's subtree.
 */
@Stable
class GlassBackdropState internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null)
    internal var position by mutableStateOf(Offset.Zero)
    internal var revision by mutableLongStateOf(0L)
        private set

    // Do not read revision while recording the source: observing and updating that same snapshot
    // value in a draw node would continuously invalidate it. Only overlays observe revision.
    private var recordings = 0L

    internal fun recorded() {
        revision = ++recordings
    }
}

@Composable
fun rememberGlassBackdrop(): GlassBackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdropState(layer) }
}

/**
 * Records page content, including its background, before displaying it normally. Place this before
 * the page's background modifier. The navigation bar must be a sibling, never a child of this node.
 * Each new recording invalidates overlays; nested graphics layers remain live RenderNode children.
 */
fun Modifier.glassBackdropSource(state: GlassBackdropState): Modifier = this
    .onGloballyPositioned {
        state.coordinates = it
        state.position = it.positionInRoot()
    }
    // Separate observation from overlays: a new source revision must not invalidate this source's
    // enclosing draw layer merely because a sibling glass node reads it.
    .graphicsLayer()
    .drawWithContent {
        // Use DrawScope's record extension, which redirects this ContentDrawScope's canvas. Calling
        // the lower-level record(density, direction, size) member would leave drawContent() drawing
        // to the original canvas and record an empty backdrop instead.
        state.layer.record(size = IntSize(size.width.toInt(), size.height.toInt())) {
            this@drawWithContent.drawContent()
        }
        state.recorded()
        drawLayer(state.layer)
    }

/**
 * Paints a live glass background without blurring this element's own content, icons, or hit targets.
 * [tint] is the overlay color, including its opacity. Android 13+ adds rounded-edge refraction;
 * Android 12 uses background blur, and older devices (or [enabled] == false) use the supplied tint.
 * Tint opacity is preserved in every fallback, including low-opacity selection indicators.
 * The source and overlay must belong to the same Compose root and use the same layout scale.
 */
fun Modifier.glassBackdrop(
    state: GlassBackdropState,
    shape: Shape,
    tint: Color,
    enabled: Boolean = true,
): Modifier {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return background(tint, shape)
    }
    return composed {
        val sample = rememberGraphicsLayer()
        val effects = remember { GlassEffectCache() }
        var overlayPosition by remember { mutableStateOf(Offset.Zero) }
        var overlayCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }

        Modifier
            .onGloballyPositioned {
                overlayCoordinates = it
                overlayPosition = it.positionInRoot()
            }
            .graphicsLayer()
            .drawWithContent {
                val outline = shape.createOutline(size, layoutDirection, this)
                val sourceReady = state.revision > 0 && state.coordinates?.isAttached == true &&
                    overlayCoordinates?.isAttached == true && !state.layer.isReleased
                if (!sourceReady || size.width <= 0f || size.height <= 0f) {
                    drawOutline(outline, tint)
                    drawContent()
                    return@drawWithContent
                }

                // Render only the small overlay area plus an apron for blur/edge sampling. The
                // full page remains a display list, rather than a full-screen offscreen bitmap.
                val padding = ceil(18.dp.toPx()).toInt()
                val offset = overlayPosition - state.position
                val bufferSize = IntSize(
                    ceil(size.width).toInt() + padding * 2,
                    ceil(size.height).toInt() + padding * 2,
                )
                val radii = (outline as? Outline.Rounded)?.roundRect
                sample.renderEffect = effects.effect(
                    GlassEffectKey(
                        width = size.width,
                        height = size.height,
                        padding = padding.toFloat(),
                        blurRadius = 5.dp.toPx(),
                        edgeWidth = 14.dp.toPx(),
                        refraction = 5.dp.toPx(),
                        topLeft = radii?.topLeftCornerRadius?.x ?: 0f,
                        topRight = radii?.topRightCornerRadius?.x ?: 0f,
                        bottomRight = radii?.bottomRightCornerRadius?.x ?: 0f,
                        bottomLeft = radii?.bottomLeftCornerRadius?.x ?: 0f,
                        rounded = radii != null,
                    ),
                )
                sample.record(size = bufferSize) {
                    // An empty/out-of-bounds source must remain transparent. Filling it with an
                    // opaque tint here would turn a 10%-alpha selection tint into solid primary.
                    translate(padding - offset.x, padding - offset.y) {
                        drawLayer(state.layer)
                    }
                }

                val clip = Path().apply {
                    when (outline) {
                        is Outline.Rectangle -> addRect(outline.rect)
                        is Outline.Rounded -> addRoundRect(outline.roundRect)
                        is Outline.Generic -> addPath(outline.path)
                    }
                }
                clipPath(clip) {
                    translate(-padding.toFloat(), -padding.toFloat()) { drawLayer(sample) }
                    drawRect(tint)
                    // A thin, directional rim remains visible over both bright and dark content.
                    drawOutline(
                        outline,
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = .40f), Color.White.copy(alpha = .05f)),
                            start = Offset.Zero,
                            end = Offset(size.width * .7f, size.height),
                        ),
                        style = Stroke(1.dp.toPx()),
                    )
                }
                // Crucially, foreground content is drawn after the sampled layer's RenderEffect.
                drawContent()
            }
    }
}

private data class GlassEffectKey(
    val width: Float,
    val height: Float,
    val padding: Float,
    val blurRadius: Float,
    val edgeWidth: Float,
    val refraction: Float,
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float,
    val rounded: Boolean,
)

/** Keeps native effects stable while content scrolls; rebuilds only when geometry/density changes. */
private class GlassEffectCache {
    private var previous: GlassEffectKey? = null
    private var cached: RenderEffect? = null

    @RequiresApi(Build.VERSION_CODES.S)
    fun effect(key: GlassEffectKey): RenderEffect {
        if (previous != key || cached == null) {
            cached = createGlassEffect(key)
            previous = key
        }
        return checkNotNull(cached)
    }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun createGlassEffect(key: GlassEffectKey): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(
        key.blurRadius,
        key.blurRadius,
        android.graphics.Shader.TileMode.CLAMP,
    )
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && key.rounded) {
        createRefractedEffect(key, blur)
    } else {
        blur.asComposeRenderEffect()
    }
}

// Kept in a separately guarded function: no RuntimeShader is created or referenced on API 26–32.
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createRefractedEffect(
    key: GlassEffectKey,
    blur: android.graphics.RenderEffect,
): RenderEffect {
    val shader = android.graphics.RuntimeShader(GLASS_SHADER).apply {
        setFloatUniform("dimensions", key.width, key.height)
        setFloatUniform("apron", key.padding)
        setFloatUniform("radii", key.topLeft, key.topRight, key.bottomRight, key.bottomLeft)
        setFloatUniform("edgeWidth", key.edgeWidth)
        setFloatUniform("refraction", key.refraction)
    }
    val lens = android.graphics.RenderEffect.createRuntimeShaderEffect(shader, "backdrop")
    return android.graphics.RenderEffect.createChainEffect(lens, blur).asComposeRenderEffect()
}

// Original AGSL: sample the live backdrop through a softly curved rounded-rectangle perimeter.
// This operates in the small overlay layer's local pixels, independent of its screen position.
private const val GLASS_SHADER = """
uniform shader backdrop;
uniform float2 dimensions;
uniform float apron;
uniform float4 radii;
uniform float edgeWidth;
uniform float refraction;

float roundedDistance(float2 p, float2 halfSize, float radius) {
    float2 q = abs(p) - halfSize + radius;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - radius;
}

half4 main(float2 coordinate) {
    float2 halfSize = dimensions * 0.5;
    float2 p = coordinate - float2(apron) - halfSize;
    float radius = p.y < 0.0 ? (p.x < 0.0 ? radii.x : radii.y)
                               : (p.x < 0.0 ? radii.w : radii.z);
    radius = min(radius, min(halfSize.x, halfSize.y));
    float distance = roundedDistance(p, halfSize, radius);
    float edge = 1.0 - clamp(-distance / max(edgeWidth, 1.0), 0.0, 1.0);
    edge *= edge;
    float2 gradient = float2(
        roundedDistance(p + float2(0.5, 0.0), halfSize, radius) -
            roundedDistance(p - float2(0.5, 0.0), halfSize, radius),
        roundedDistance(p + float2(0.0, 0.5), halfSize, radius) -
            roundedDistance(p - float2(0.0, 0.5), halfSize, radius)
    );
    float2 normal = gradient / max(length(gradient), 0.001);
    half4 sampledColor = backdrop.eval(coordinate - normal * refraction * edge);
    half luminance = dot(sampledColor.rgb, half3(0.2126, 0.7152, 0.0722));
    half3 vibrant = mix(half3(luminance), sampledColor.rgb, 1.10);
    float light = pow(max(dot(normal, normalize(float2(-0.6, -0.8))), 0.0), 3.0);
    vibrant = mix(vibrant, half3(sampledColor.a), half(edge * light * 0.16));
    return half4(clamp(vibrant, 0.0, sampledColor.a), sampledColor.a);
}
"""
