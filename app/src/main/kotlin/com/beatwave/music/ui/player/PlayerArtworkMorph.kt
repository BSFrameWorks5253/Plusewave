/**
 * Pulse Wave Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */
package com.beatwave.music.ui.player

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size as CoilSize
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.beatwave.music.R
import kotlinx.coroutines.delay
import androidx.compose.foundation.Canvas
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size as GeometrySize
import androidx.compose.ui.graphics.Brush
import com.beatwave.music.LocalPlayerConnection
import com.beatwave.music.constants.CrossfadeBurnMorphKey
import com.beatwave.music.utils.rememberPreference
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Registered bounds of the mini player's own artwork and the full player's own artwork,
 * in root/window coordinates -- updated from `onGloballyPositioned` on each, read once
 * by [PlayerArtworkMorphOverlay].
 *
 * Plain module-level state, not a hoisted parameter, for the same reason
 * `SharedArtwork.kt`'s `placedArtwork` map is plain state: the two artworks live in
 * composables that are far apart in the tree (`MiniPlayer.kt`'s collapsed content,
 * `Thumbnail.kt`'s expanded content, orchestrated from `BottomSheet.kt` in between) and
 * neither needs to compose off the other's position -- only the overlay, drawn once,
 * reads both.
 */
private val miniArtworkRect = mutableStateOf<Rect?>(null)
private val fullArtworkRect = mutableStateOf<Rect?>(null)

/**
 * Call on the mini player's own artwork box. Purely observational: records where the
 * artwork is on screen and changes nothing about how the mini player itself renders.
 */
/**
 * Corner radius of each end's cover, in px.
 *
 * The flight used to clip to [CircleShape] the whole way, so a rounded-square pill cover
 * snapped to a circle the instant it left the bar and snapped back to the player's own
 * shape on arrival. The radius has to travel with the rect.
 *
 * A vinyl or clover player artwork registers half its size here, i.e. a circle; the
 * clover's petals are not reproduced mid-flight, which reads as a circle growing into a
 * clover at the very end.
 */
private val miniArtworkRadius = androidx.compose.runtime.mutableFloatStateOf(0f)
private val fullArtworkRadius = androidx.compose.runtime.mutableFloatStateOf(0f)

fun Modifier.registerMiniArtworkRect(cornerRadiusPx: Float = 0f): Modifier = onGloballyPositioned {
    if (it.isAttached) {
        val bounds = it.boundsInRoot()
        if (miniArtworkRect.value != bounds) {
            miniArtworkRect.value = bounds
        }
        if (miniArtworkRadius.floatValue != cornerRadiusPx) {
            miniArtworkRadius.floatValue = cornerRadiusPx
        }
    }
}

/** Call on the full player's own artwork container. Same contract as
 *  [registerMiniArtworkRect]. */
fun Modifier.registerFullArtworkRect(cornerRadiusPx: Float = 0f): Modifier = onGloballyPositioned {
    // The player's artwork lives in a pager, so every neighbouring page reports bounds
    // too -- and the last one to report would win. Only the page actually on screen may
    // claim the target rect; an off-screen page's window bounds are empty.
    if (it.boundsInWindow().isEmpty) return@onGloballyPositioned
    if (it.isAttached) {
        val bounds = it.boundsInRoot()
        if (fullArtworkRect.value != bounds) {
            fullArtworkRect.value = bounds
        }
        if (fullArtworkRadius.floatValue != cornerRadiusPx) {
            fullArtworkRadius.floatValue = cornerRadiusPx
        }
    }
}

/**
 * Draws ONE artwork image growing from the mini player's registered rect to the full
 * player's, as [progress] runs 0..1.
 *
 * Sized to the FULL (target) rect, not the mini one -- scaling DOWN from there to the
 * mini size at fraction 0, then back up to 1:1 (its native size) at the handoff. This
 * is the opposite of the first version, which sized to the small mini rect and scaled
 * UP: scaling up stretches whatever resolution the underlying image happens to be,
 * which read as soft mid-growth even after requesting a larger decode, because the
 * COMPOSE LAYOUT SIZE (what a bitmap gets stretched across) was still the tiny mini
 * box the whole time. Sizing to full and scaling down means the on-screen image is
 * never asked to be bigger than the pixels it actually has -- downsampling stays
 * sharp at any fraction, which is exactly the technique Melox's own
 * `PlayerSheetArtworkOverlay` uses (see the scratchpad clone read for this fix).
 *
 * `transformOrigin` pinned to the top-left (not the layer's default center) so a
 * plain `translationX/Y = rect.left/top` places the SCALED result exactly at
 * [sharedArtworkRect]'s interpolated rect, with no extra center-offset math needed.
 *
 * A fresh [AsyncImage] of the same URL, not a captured GraphicsLayer of the mini
 * player's own rendering (Melox's technique) -- both artworks show identical content,
 * so a pixel capture buys nothing a positioned image doesn't, and Coil's cache is
 * already warm from the mini player's own load.
 *
 Its corner radius travels with it, from the pill cover's to whatever the player's own
 * artwork uses (a card's radius, or half its size for the round vinyl), so the cover
 * grows into the real shape instead of snapping to a circle on the way.
 */
@Composable
fun PlayerArtworkMorphOverlay(
    thumbnailUrl: String?,
    progress: () -> Float,
) {
    val mini = miniArtworkRect.value
    val full = fullArtworkRect.value
    if (mini == null || full == null || thumbnailUrl == null) return
    // Tied to the container morph, not to raw progress: on a layout with no pill to fly
    // out of (classic nav bar, rail, tab view) the sheet still just slides, and a cover
    // flying across it would be flying out of nowhere.
    if (!PlayerMorph.active) return
    // Both rects are registered from onGloballyPositioned, which can fire once with
    // a degenerate (zero or negative) size before the real layout pass settles --
    // measured a crash on a Samsung device: coil3.size.Dimension throws on any px
    // <= 0, and full.width/height fed CoilSize directly with no floor.
    if (mini.width <= 0f || mini.height <= 0f || full.width <= 0f || full.height <= 0f) return

    val density = LocalDensity.current
    val boxWidthDp = with(density) { full.width.toDp() }
    val boxHeightDp = with(density) { full.height.toDp() }

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    full.left.roundToInt(),
                    full.top.roundToInt(),
                )
            }
            .size(boxWidthDp, boxHeightDp)
            .graphicsLayer {
                val fraction = progress().coerceIn(0f, 1f)
                if (fraction <= 0f || fraction >= 1f) {
                    alpha = 0f
                    return@graphicsLayer
                }
                alpha = 1f
                val rect = sharedArtworkRect(mini, full, fraction)
                transformOrigin = TransformOrigin(0f, 0f)
                translationX = rect.left - full.left
                translationY = rect.top - full.top
                val artworkScaleX = rect.width / full.width.coerceAtLeast(1f)
                val artworkScaleY = rect.height / full.height.coerceAtLeast(1f)
                scaleX = artworkScaleX
                scaleY = artworkScaleY
                val radius = androidx.compose.ui.util.lerp(
                    miniArtworkRadius.floatValue,
                    fullArtworkRadius.floatValue,
                    fraction,
                )
                val localRadius = if (artworkScaleX > 0f) (radius / artworkScaleX).toDp() else radius.toDp()
                shape = RoundedCornerShape(localRadius)
                clip = true
            }
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(thumbnailUrl)
                .size(CoilSize(full.width.toInt(), full.height.toInt()))
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(boxWidthDp, boxHeightDp),
        )
    }
}

/**
 * Hides the full player's own artwork while [PlayerArtworkMorphOverlay] is in flight.
 *
 * The overlay draws the cover for the whole 0..1 journey, but the sheet's content is
 * already opaque from the handoff onward -- so without this, both are on screen from a
 * quarter of the way in: the real one sitting at its final position while the travelling
 * one is still climbing towards it.
 */
fun Modifier.hideWhileArtworkMorphing(progress: () -> Float): Modifier = graphicsLayer {
    // Lambda, not a Float: this modifier sits deep inside the player's content (under
    // AnimatedContent and a shared-element LookaheadScope on the V2 player). Taking the
    // value meant the caller read the sheet's progress in COMPOSITION, so that whole
    // subtree recomposed on every frame of every open/close drag -- which is what put
    // measure requests on nodes the same frame was deactivating ("measure is called on
    // a deactivated node"). Reading it here keeps it to the draw phase.
    val fraction = progress()
    alpha = if (fraction > 0f && fraction < 1f) 0f else 1f
}

/**
 * Renders an intense burning/dissolving wavefront with glowing incandescent embers and drifting sparks.
 */
/**
 * Holographic glitch and chromatic prism overlay matching convxdemo.mp4:
 * - Upper-left corner chromatic prism flare streak
 * - Dynamic horizontal chromatic glitch scanline bars
 * - Fine digital CRT scanline texture
 */
@Composable
fun HolographicGlitchOverlay(
    progress: Float,
    modifier: Modifier = Modifier
) {
    if (progress <= 0.005f || progress >= 0.995f) return

    val density = LocalDensity.current
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val peak = kotlin.math.sin(progress * Math.PI.toFloat())

        if (peak <= 0.04f) return@Canvas

        // 1. Top-left angled rainbow chromatic prism light flare (matching convxdemo.mp4)
        val flareAlpha = (peak * 0.55f).coerceIn(0f, 1f)
        val flareBrush = Brush.linearGradient(
            colors = listOf(
                Color(0x0000E5FF),
                Color(0xFF00E5FF).copy(alpha = flareAlpha * 0.85f),
                Color(0xFFFF007F).copy(alpha = flareAlpha * 0.75f),
                Color(0xFFFFEA00).copy(alpha = flareAlpha * 0.60f),
                Color(0x00FFEA00)
            ),
            start = Offset(0f, 0f),
            end = Offset(w * 0.55f, h * 0.55f)
        )
        drawRect(
            brush = flareBrush,
            size = GeometrySize(w, h),
            blendMode = BlendMode.Screen
        )

        // 2. Horizontal chromatic glitch scanline displacement bars across artwork
        val sliceCount = 16
        for (i in 0 until sliceCount) {
            val sliceY = (i.toFloat() / sliceCount) * h
            val sliceH = (h / sliceCount) * 0.90f
            val wave = kotlin.math.sin((i * 1.6) + (progress * 24.0)).toFloat()
            val sliceShift = wave * with(density) { 14.dp.toPx() } * peak

            if (kotlin.math.abs(wave) > 0.25f) {
                // Cyan channel edge
                drawRect(
                    color = Color(0xFF00E5FF).copy(alpha = 0.28f * peak),
                    topLeft = Offset(sliceShift, sliceY),
                    size = GeometrySize(w, sliceH),
                    blendMode = BlendMode.Screen
                )
                // Magenta channel edge
                drawRect(
                    color = Color(0xFFFF007F).copy(alpha = 0.28f * peak),
                    topLeft = Offset(-sliceShift * 0.90f, sliceY),
                    size = GeometrySize(w, sliceH),
                    blendMode = BlendMode.Screen
                )
            }
        }

        // 3. Fine holographic horizontal scanlines
        val scanlineStep = with(density) { 4.dp.toPx() }
        var y = 0f
        while (y < h) {
            drawLine(
                color = Color.Black.copy(alpha = 0.08f * peak),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = with(density) { 1.dp.toPx() },
                blendMode = BlendMode.Darken
            )
            y += scanlineStep
        }
    }
}

/** Legacy aliases for backwards compatibility */
@Composable
fun ParticleMorphOverlay(progress: Float, modifier: Modifier = Modifier) =
    HolographicGlitchOverlay(progress = progress, modifier = modifier)

@Composable
fun MorphingBurnOverlay(progress: Float, modifier: Modifier = Modifier) =
    HolographicGlitchOverlay(progress = progress, modifier = modifier)

/**
 * Renders album cover art that morphs and cross-fades smoothly into the next song's
 * cover art during track changes (both auto-crossfade and manual skip/seek), matching
 * convxdemo.mp4 with holographic double-exposure morph and chromatic aberration.
 */
@Composable
fun MorphingCoverArt(
    artworkUri: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    contentDescription: String? = "Cover Art",
    animationDurationMillis: Int = 1350,
) {
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current
    val isCrossfading by (playerConnection?.isCrossfading ?: MutableStateFlow(false)).collectAsState()
    val crossfadeDurationMs = playerConnection?.crossfadeDurationMs ?: 3000f
    val crossfadeBurnMorph by rememberPreference(CrossfadeBurnMorphKey, defaultValue = true)

    var currentUrl by remember { mutableStateOf(artworkUri) }
    var outgoingUrl by remember { mutableStateOf<String?>(null) }
    val progress = remember { Animatable(1f) }

    // Start morphing animation IMMEDIATELY as soon as song starts crossfading or track changes (manual skip or auto)
    LaunchedEffect(artworkUri) {
        val targetUri = artworkUri
        if (targetUri != currentUrl) {
            val oldUrl = currentUrl
            if (oldUrl != null && targetUri != null) {
                // Immediately swap URLs so:
                // - outgoingUrl holds the OLD song art (Song A)
                // - currentUrl holds the NEW incoming song art (Song B)
                outgoingUrl = oldUrl
                currentUrl = targetUri
                progress.snapTo(0f)
                val effectiveDuration = if (isCrossfading) {
                    crossfadeDurationMs.toInt().coerceIn(1200, 6000)
                } else {
                    1050
                }
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = effectiveDuration,
                        easing = CubicBezierEasing(0.22f, 1.0f, 0.36f, 1.0f)
                    )
                )
                outgoingUrl = null
            } else {
                currentUrl = targetUri
            }
        }
    }

    val p = progress.value
    val peakIntensity = kotlin.math.sin(p * Math.PI.toFloat())

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // LAYER 1 (BASE): Incoming artwork layer — expands & ascends into focus!
        val curUrl = currentUrl
        if (!curUrl.isNullOrBlank()) {
            var activeCurUrl by remember(curUrl) { mutableStateOf<String?>(curUrl) }
            val inAlpha = if (outgoingUrl != null && !crossfadeBurnMorph) p.coerceIn(0f, 1f) else 1f
            val inTranslateY = if (outgoingUrl != null && crossfadeBurnMorph) with(LocalDensity.current) { (18.dp * (1f - p)).toPx() } else 0f
            val inScale = if (outgoingUrl != null && crossfadeBurnMorph) (0.94f + (0.06f * p)) else 1f

            // Chromatic aberration morph effect during track transition (matching convxdemo.mp4)
            val isMorphing = outgoingUrl != null && crossfadeBurnMorph && p in 0.02f..0.98f
            val morphPeak = if (isMorphing) peakIntensity else 0f
            val chromaticOffsetPx = morphPeak * with(LocalDensity.current) { 10.dp.toPx() }

            if (morphPeak > 0.05f) {
                // Cyan channel shift (left)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(activeCurUrl)
                        .size(CoilSize.ORIGINAL)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    colorFilter = ColorFilter.tint(Color(0xFF00E5FF).copy(alpha = 0.45f * morphPeak), BlendMode.Screen),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (inAlpha * 0.85f).coerceIn(0f, 1f)
                            translationX = -chromaticOffsetPx * 0.85f
                            translationY = inTranslateY
                            scaleX = inScale
                            scaleY = inScale
                        }
                )

                // Magenta/Red channel shift (right)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(activeCurUrl)
                        .size(CoilSize.ORIGINAL)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    colorFilter = ColorFilter.tint(Color(0xFFFF007F).copy(alpha = 0.45f * morphPeak), BlendMode.Screen),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (inAlpha * 0.85f).coerceIn(0f, 1f)
                            translationX = chromaticOffsetPx * 0.85f
                            translationY = inTranslateY
                            scaleX = inScale
                            scaleY = inScale
                        }
                )
            }

            // Primary incoming artwork image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeCurUrl)
                    .size(CoilSize.ORIGINAL)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                onError = {
                    activeCurUrl = nextThumbnailFallback(activeCurUrl)
                },
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = inAlpha
                        translationY = inTranslateY
                        scaleX = inScale
                        scaleY = inScale
                    }
            )
        } else if (outgoingUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.pulsewave_notification),
                    contentDescription = contentDescription,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        // LAYER 2 (TOP): Outgoing artwork layer — morphs with full chromatic aberration & holographic double-exposure!
        val outUrl = outgoingUrl
        if (!outUrl.isNullOrBlank() && p < 1f) {
            var activeOutUrl by remember(outUrl) { mutableStateOf<String?>(outUrl) }
            val outAlpha = (1f - p).coerceIn(0f, 1f)
            val outScale = if (crossfadeBurnMorph) 1f + (0.04f * p) else 1f
            val outTranslateY = if (crossfadeBurnMorph) with(LocalDensity.current) { (-14.dp * p).toPx() } else 0f

            val isMorphing = crossfadeBurnMorph && p in 0.02f..0.98f
            val morphPeak = if (isMorphing) peakIntensity else 0f
            val chromaticOffsetPx = morphPeak * with(LocalDensity.current) { 10.dp.toPx() }

            if (morphPeak > 0.05f) {
                // Outgoing Cyan channel shift (left)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(activeOutUrl)
                        .size(CoilSize.ORIGINAL)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    colorFilter = ColorFilter.tint(Color(0xFF00E5FF).copy(alpha = 0.45f * morphPeak), BlendMode.Screen),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (outAlpha * 0.85f).coerceIn(0f, 1f)
                            translationX = -chromaticOffsetPx
                            translationY = outTranslateY
                            scaleX = outScale
                            scaleY = outScale
                        }
                )

                // Outgoing Magenta channel shift (right)
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(activeOutUrl)
                        .size(CoilSize.ORIGINAL)
                        .crossfade(false)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    colorFilter = ColorFilter.tint(Color(0xFFFF007F).copy(alpha = 0.45f * morphPeak), BlendMode.Screen),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = (outAlpha * 0.85f).coerceIn(0f, 1f)
                            translationX = chromaticOffsetPx
                            translationY = outTranslateY
                            scaleX = outScale
                            scaleY = outScale
                        }
                )
            }

            // Primary outgoing artwork image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(activeOutUrl)
                    .size(CoilSize.ORIGINAL)
                    .crossfade(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .build(),
                onError = { activeOutUrl = nextThumbnailFallback(activeOutUrl) },
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = outAlpha
                        translationY = outTranslateY
                        scaleX = outScale
                        scaleY = outScale
                    }
            )
        }

        // LAYER 3 (OVERLAY): Holographic glitch scanlines and prism flare matching convxdemo.mp4
        if (crossfadeBurnMorph && outgoingUrl != null && p in 0.005f..0.995f) {
            HolographicGlitchOverlay(
                progress = p,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}


