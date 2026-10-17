package com.zfl.launcher.ui.glass

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.unit.Density
import com.zfl.launcher.ui.theme.LiquidGlassTokens

/** Log tag of glass rendering (shader compilation, tilt sensor). */
internal const val GLASS_TAG: String = "ZflGlass"

/**
 * AGSL source of Liquid Glass. The geometry mirrors GlassGeometry.kt (`sdf` ↔
 * [GlassOutline.signedDistance], the bevel ↔ [lensBend]); all tuning comes in as uniforms
 * from LiquidGlassTokens, so there are no magic numbers here besides Rec. 709 luma weights.
 *
 * Coordinates: `fragCoord` is local to the element (px), the drawn rectangle extends past it
 * by the shadow radius; `origin` is the element's top-left corner in the window, so
 * `origin + fragCoord` addresses the wallpaper images, which are laid out like the screen and
 * downscaled by `imageScale`.
 */
internal const val LIQUID_GLASS_AGSL: String = """
uniform shader sharpImage;   // the wallpaper, unblurred
uniform shader bodyImage;    // the wallpaper, saturated, with a light blur mixed in
uniform float imageScale;    // window px -> image px (both images share the scale)

// Set once per density (see LiquidGlassShader.setConstants).
uniform float4 optics;       // aberration, edge clarity, saturation, bevel glow ratio
uniform float4 specular;     // rim width px, reflex ratio, ambient share, falloff
uniform float2 shadowShape;  // radius px, offset y px
uniform float2 touchGlow;    // light over the whole element, extra light in the spot

// Per element.
uniform float2 size;
uniform float2 origin;
uniform float4 corners;      // corner extents: top-left, top-right, bottom-right, bottom-left
uniform float exponent;      // 2 = circular corners, ~2.6 = continuous corners
uniform float2 lens;         // bevel width px, inward refraction px
uniform float4 tint;         // straight RGBA laid over the glass
uniform float4 rimColor;     // specular color, alpha = strength
uniform float2 lightDir;     // unit vector towards the light, y down
uniform float shadowOpacity;
uniform float4 touch;        // x, y (local px), amount 0..1, spot radius px

float cornerAt(float2 p) {
    float2 r = p.x > 0.0 ? corners.yz : corners.xw;
    return p.y > 0.0 ? r.y : r.x;
}

// Signed distance to the contour (negative inside) and the outward unit normal.
float sdf(float2 p, out float2 normal) {
    float a = cornerAt(p);
    float2 q = abs(p) - size * 0.5 + a;
    float2 s = float2(p.x > 0.0 ? 1.0 : -1.0, p.y > 0.0 ? 1.0 : -1.0);
    if (q.x > 0.0 && q.y > 0.0) {
        float2 u = q / a;
        float norm = pow(pow(u.x, exponent) + pow(u.y, exponent), 1.0 / exponent);
        float2 g = pow(u / norm, float2(exponent - 1.0));
        float len = length(g);
        normal = s * g / len;
        return (norm - 1.0) * a / len;
    }
    if (q.x > q.y) {
        normal = float2(s.x, 0.0);
        return q.x - a;
    }
    normal = float2(0.0, s.y);
    return q.y - a;
}

float3 touchLight(float2 fragCoord) {
    if (touch.z <= 0.0) {
        return float3(0.0);
    }
    float spot = smoothstep(touch.w, touch.w * 0.5, distance(fragCoord, touch.xy));
    return float3(touch.z * (touchGlow.x + touchGlow.y * spot));
}

// fragCoord is local to the glass; the drawn rectangle reaches past it for the shadow.
half4 main(float2 fragCoord) {
    float2 n;
    float d = sdf(fragCoord - size * 0.5, n);
    float depth = -d;
    float2 w = origin + fragCoord;

    // The flat middle, most of the pixels: one lookup, like Frosted glass.
    if (depth >= lens.x && depth >= specular.x) {
        float3 middle = mix(bodyImage.eval(w * imageScale).rgb, tint.rgb, tint.a) + touchLight(fragCoord);
        return half4(half3(clamp(middle, 0.0, 1.0)), 1.0);
    }

    // Soft shadow around the glass, a little lower, so it floats above the wallpaper.
    float coverage = clamp(0.5 - d, 0.0, 1.0);
    float shadowAlpha = 0.0;
    if (coverage < 1.0) {
        float2 shadowNormal;
        float ds = sdf(fragCoord - float2(0.0, shadowShape.y) - size * 0.5, shadowNormal);
        float fade = 1.0 - clamp(ds / shadowShape.x, 0.0, 1.0);
        shadowAlpha = shadowOpacity * fade * fade * (1.0 - coverage);
        if (coverage <= 0.0) {
            return half4(0.0, 0.0, 0.0, half(shadowAlpha));
        }
    }
    depth = max(depth, 0.0);

    // Convex bevel: towards the edge the sample moves inwards along the normal, so the
    // background is magnified and bent around the edge, like light through thick glass.
    float inv = 1.0 - clamp(depth / lens.x, 0.0, 1.0);
    float bend = 1.0 - sqrt(max(1.0 - inv * inv, 0.0));
    w -= n * (bend * lens.y);
    float2 c = w * imageScale;
    float3 col = bodyImage.eval(c).rgb;
    if (bend > 0.0) {
        float2 spread = n * (bend * lens.y * optics.x * imageScale);
        float3 sharp = float3(sharpImage.eval(c - spread).r, sharpImage.eval(c).g, sharpImage.eval(c + spread).b);
        float l = dot(sharp, float3(0.2126, 0.7152, 0.0722));
        sharp = clamp(mix(float3(l), sharp, optics.z), 0.0, 1.0);
        col = mix(col, sharp, optics.y * smoothstep(0.0, 1.0, inv));
    }
    col = mix(col, tint.rgb, tint.a);

    // Specular rim: bright where the edge faces the light, weaker on the opposite side.
    float facing = dot(n, lightDir);
    float lit = pow(max(facing, 0.0), specular.w) + specular.y * pow(max(-facing, 0.0), specular.w);
    float edge = 1.0 - clamp(depth / specular.x, 0.0, 1.0);
    float rim = edge * edge * (specular.z + lit) + optics.w * bend * lit;
    col += rimColor.rgb * (rimColor.a * rim) + touchLight(fragCoord);
    col = clamp(col, 0.0, 1.0);
    // Premultiplied glass over its own shadow at the anti-aliased edge.
    return half4(half3(col * coverage), half(coverage + shadowAlpha));
}
"""

/**
 * True when Liquid glass can be drawn on this device: Android 13+ and the shader compiled
 * (compiles it on first call). Main thread only.
 */
fun isLiquidGlassReady(): Boolean =
    Build.VERSION.SDK_INT >= LIQUID_GLASS_MIN_SDK && LiquidGlassShader.get() != null

/**
 * The one compiled Liquid Glass shader of the process. AGSL is compiled on the device, so
 * creating it may fail (driver, vendor build): that is logged once and every glass surface
 * stays Frosted. All nodes share the instance; each sets its uniforms right before drawing
 * (Android snapshots a RuntimeShader's uniforms when a draw call is recorded).
 */
@RequiresApi(LIQUID_GLASS_MIN_SDK)
internal object LiquidGlassShader {
    private var shader: RuntimeShader? = null
    private var failed = false

    // Wallpaper images currently bound as the shader's inputs, and the density of the constants.
    private var boundSharp: ImageBitmap? = null
    private var boundBody: ImageBitmap? = null
    private var constantsDensity = 0f

    /** The compiled shader, or null when compilation failed. Main thread only. */
    fun get(): RuntimeShader? {
        if (failed) return null
        shader?.let { return it }
        return try {
            RuntimeShader(LIQUID_GLASS_AGSL).also { shader = it }
        } catch (e: Throwable) {
            // IllegalArgumentException with the compiler's message, or a broken platform build.
            Log.e(GLASS_TAG, "Liquid Glass shader unavailable, falling back to Frosted", e)
            failed = true
            null
        }
    }

    /** Points the shader's image inputs at [sharp] and [body]; cheap when they are already bound. */
    fun bind(shader: RuntimeShader, sharp: ImageBitmap, body: ImageBitmap, imageScale: Float) {
        if (sharp === boundSharp && body === boundBody) return
        shader.setInputShader("sharpImage", bitmapShader(sharp))
        shader.setInputShader("bodyImage", bitmapShader(body))
        shader.setFloatUniform("imageScale", imageScale)
        boundSharp = sharp
        boundBody = body
    }

    /**
     * Uniforms that only depend on the tokens and the screen density. They stay in the shared
     * shader between draws, so they are set once instead of for every element on every frame.
     */
    fun setConstants(shader: RuntimeShader, density: Density) {
        if (density.density == constantsDensity) return
        constantsDensity = density.density
        with(density) {
            shader.setFloatUniform(
                "optics",
                LiquidGlassTokens.Aberration,
                LiquidGlassTokens.EdgeClarity,
                WallpaperBlur.SATURATION,
                LiquidGlassTokens.RimGlow / LiquidGlassTokens.HighlightStrength,
            )
            shader.setFloatUniform(
                "specular",
                LiquidGlassTokens.HighlightWidth.toPx(),
                LiquidGlassTokens.ReflexRatio,
                LiquidGlassTokens.HighlightAmbient,
                LiquidGlassTokens.SpecularFalloff,
            )
            shader.setFloatUniform("shadowShape", LiquidGlassTokens.ShadowRadius.toPx(), LiquidGlassTokens.ShadowOffsetY.toPx())
            shader.setFloatUniform("touchGlow", LiquidGlassTokens.TouchGlowAll, LiquidGlassTokens.TouchGlowSpot)
        }
    }

    private fun bitmapShader(image: ImageBitmap): BitmapShader =
        BitmapShader(image.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            filterMode = BitmapShader.FILTER_MODE_LINEAR
        }
}
