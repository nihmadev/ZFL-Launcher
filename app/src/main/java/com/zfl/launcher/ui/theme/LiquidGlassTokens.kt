package com.zfl.launcher.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lens parameters of Liquid Glass (see `ui/glass/LiquidGlassShader.kt`). Every number the
 * shader uses comes from here; the shader itself has no tuning constants.
 *
 * The model follows Apple's description of the material ("bends, shapes and concentrates
 * light" instead of scattering it) and the common reconstruction of it: a convex bevel that
 * pulls the background in from the inside (magnifying it at the edge), little blur, a
 * specular rim, a soft shadow and a gel-like reaction to touch.
 */
object LiquidGlassTokens {
    /** Width of the refracting bevel of panels: the dock, cards, sheets. */
    val PanelRim: Dp = 18.dp
    /** Bevel of capsule buttons and fields: the Search pill, the search field. */
    val ButtonRim: Dp = 12.dp
    /** Thinner lens of small round buttons (edit mode Done). */
    val SmallButtonRim: Dp = 8.dp
    /** Bevel of icon-sized tiles: folder icons, the merge halo. */
    val TileRim: Dp = 10.dp

    /** Inward sample offset at the very edge at full user strength, relative to the bevel width. */
    const val MaxRefraction: Float = 2.5f
    /** Spread of the red and blue channels relative to the offset: a hint of chromatic aberration. */
    const val Aberration: Float = 0.08f

    /**
     * Share of a light blur in the flat middle of the glass ("softer scattering" of thick
     * glass); the rest is the sharp wallpaper, so what lies under the glass stays recognisable.
     */
    const val Frost: Float = 0.4f
    /** Share of the sharp, refracted wallpaper at the very edge of the bevel. */
    const val EdgeClarity: Float = 0.8f

    /** Brightness of the specular rim on the side facing the light. */
    const val HighlightStrength: Float = 1.0f
    /** How far the specular rim reaches into the glass from its edge. */
    val HighlightWidth: Dp = 1.5.dp
    /** Strength of the rim on the side away from the light, relative to the lit side. */
    const val ReflexRatio: Float = 0.55f
    /** Part of the rim that runs around the whole contour, whatever the light direction. */
    const val HighlightAmbient: Float = 0.12f
    /** Sharpness of the specular rim around the light direction: higher is a shorter arc. */
    const val SpecularFalloff: Float = 2f
    /** Light comes from the upper left, like on iOS; the tilt sensor swings it from here. */
    const val LightBaseDegrees: Float = -35f

    /** Soft glow of the bevel towards the light: thick glass gathers light. */
    const val RimGlow: Float = 0.10f

    /** Shadow under the glass, so it floats above the wallpaper. */
    val ShadowRadius: Dp = 10.dp
    val ShadowOffsetY: Dp = 2.dp
    const val ShadowOpacity: Float = 0.22f
    /** Over a light background the shadow is lighter (it would read as dirt), like iOS does. */
    const val LightShadowReduction: Float = 0.5f

    // Adaptive tone: the luminance (0..1) of the wallpaper under the element picks the tint.
    /** Below this luminance the background counts as fully dark: the white tint is boosted. */
    const val ToneDark: Float = 0.15f
    /** From here on the tint starts turning grey; fully grey at [ToneLightEnd]. */
    const val ToneLightStart: Float = 0.5f
    const val ToneLightEnd: Float = 0.8f
    /** Extra white tint on a dark background, relative to the user's glass brightness. */
    const val DarkTintBoost: Float = 0.35f
    /** Grey level the tint turns to on a light background (0 black, 1 white). */
    const val LightTintLevel: Float = 0.1f
    /** Extra specular on a light background, where white alone washes out. */
    const val LightHighlightBoost: Float = 0.6f

    // Touch: the glass lights up from under the finger, grows a little and follows the finger like a gel.
    /** Light added over the whole element at the peak of a press. */
    const val TouchGlowAll: Float = 0.08f
    /** Extra light in a spot under the finger. */
    const val TouchGlowSpot: Float = 0.15f
    /** Radius of that spot relative to the shorter side of the element. */
    const val TouchGlowRadius: Float = 1.5f
    /** How much a pressed element grows, in total (not per side). */
    val PressGrow: Dp = 4.dp
    /** How far a drag pulls the element: the slope of a tanh limited to its shorter side. */
    const val DragFollow: Float = 0.05f
    /** Extra stretch along the drag at most. */
    val DragStretch: Dp = 4.dp

    /** The highlight never swings further than this from its base direction. */
    const val TiltMaxDegrees: Float = 35f
    /** Low-pass factor per sensor sample (50 Hz): ~0.15 s to settle. */
    const val TiltSmoothing: Float = 0.12f
    /** Smaller changes are not published: a phone at rest causes no redraws. */
    const val TiltDeadbandDegrees: Float = 0.4f
    /** Sensor period; slower than the display, the smoothing hides the steps. */
    const val TiltSamplingUs: Int = 20_000
}
