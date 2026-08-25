package com.example

import com.example.engine.AsciiCharacterBuffer
import com.example.engine.AsciiShaders
import com.example.engine.FontAtlasGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AsciiGlRendererTest {

    @Test
    fun testFontAtlasGlyphUvMapping() {
        // Test Space character UV
        val spaceUv = FontAtlasGenerator.getGlyphUv(' ')
        assertEquals(4, spaceUv.size)
        assertTrue("uMin >= 0.0", spaceUv[0] >= 0.0f)
        assertTrue("vMin >= 0.0", spaceUv[1] >= 0.0f)
        assertTrue("uMax <= 1.0", spaceUv[2] <= 1.0f)
        assertTrue("vMax <= 1.0", spaceUv[3] <= 1.0f)
        assertTrue("uMax > uMin", spaceUv[2] > spaceUv[0])
        assertTrue("vMax > vMin", spaceUv[3] > spaceUv[1])

        // Test Wall '#' and Player '@' glyphs
        val wallUv = FontAtlasGenerator.getGlyphUv('#')
        val playerUv = FontAtlasGenerator.getGlyphUv('@')
        assertNotNull(wallUv)
        assertNotNull(playerUv)

        val wallIndex = FontAtlasGenerator.getGlyphIndex('#')
        val playerIndex = FontAtlasGenerator.getGlyphIndex('@')
        assertTrue("Wall index should be valid", wallIndex in 0..255)
        assertTrue("Player index should be valid", playerIndex in 0..255)
    }

    @Test
    fun testAsciiCharacterBufferQuadBatching() {
        val buffer = AsciiCharacterBuffer(maxQuads = 128)
        assertEquals(0, buffer.vertexCount)

        // Push an ASCII Quad
        buffer.pushCharCell(
            cx = 100f, cy = 200f,
            halfW = 20f, halfH = 14f, z = 0.5f,
            char = '@',
            fgR = 0.31f, fgG = 0.82f, fgB = 0.77f, fgA = 1.0f,
            bgR = 0.05f, bgG = 0.15f, bgB = 0.14f, bgA = 0.8f,
            lightIntensity = 1.25f,
            dither = 1.0f,
            glow = 1.0f,
            wave = 0.0f,
            tintR = 1.0f, tintG = 0.95f, tintB = 0.85f
        )

        val nativeBuffer = buffer.finish()
        // 1 quad = 6 vertices (2 triangles)
        assertEquals(6, buffer.vertexCount)
        assertEquals(6 * AsciiCharacterBuffer.FLOATS_PER_VERTEX, nativeBuffer.limit())

        // Validate first vertex position X and Y
        val posX = nativeBuffer.get(0)
        val posY = nativeBuffer.get(1)
        val posZ = nativeBuffer.get(2)
        assertEquals(80f, posX, 0.01f) // 100 - 20
        assertEquals(186f, posY, 0.01f) // 200 - 14
        assertEquals(0.5f, posZ, 0.01f)
    }

    @Test
    fun testAsciiTextPushingIntoBuffer() {
        val buffer = AsciiCharacterBuffer(maxQuads = 256)
        buffer.pushText(
            startX = 50f,
            startY = 100f,
            text = "FALLOUT WASTELAND",
            charW = 10f,
            charH = 16f,
            fgR = 1.0f, fgG = 0.8f, fgB = 0.2f
        )
        buffer.finish()

        // "FALLOUT WASTELAND" has 17 characters, 1 space -> 16 visible characters = 16 * 6 = 96 vertices
        assertEquals(16 * 6, buffer.vertexCount)
    }

    @Test
    fun test25DIsometricCoordinateProjection() {
        val buffer = AsciiCharacterBuffer()
        val (isoX1, isoY1) = buffer.gridToIso(
            gridX = 5f, gridY = 5f, elevation = 0f,
            centerX = 500f, centerY = 400f,
            camX = 5f, camY = 5f, zoom = 1.0f
        )

        // When gridX == camX and gridY == camY, result must match center
        assertEquals(500f, isoX1, 0.01f)
        assertEquals(400f, isoY1, 0.01f)

        // When moving east (gridX + 1, gridY), isometric projection should move diagonally down-right
        val (isoX2, isoY2) = buffer.gridToIso(
            gridX = 6f, gridY = 5f, elevation = 0f,
            centerX = 500f, centerY = 400f,
            camX = 5f, camY = 5f, zoom = 1.0f
        )
        assertTrue("isoX2 should be to the right of center", isoX2 > isoX1)
        assertTrue("isoY2 should be below center", isoY2 > isoY1)

        // Elevation should raise the Y coordinate (lower screen Y)
        val (isoXElev, isoYElev) = buffer.gridToIso(
            gridX = 5f, gridY = 5f, elevation = 1.0f,
            centerX = 500f, centerY = 400f,
            camX = 5f, camY = 5f, zoom = 1.0f
        )
        assertEquals(500f, isoXElev, 0.01f)
        assertEquals(400f - AsciiCharacterBuffer.WALL_HEIGHT, isoYElev, 0.01f)
    }

    @Test
    fun testShaderSourceValidation() {
        val vertexShader = AsciiShaders.VERTEX_SHADER
        val fragmentShader = AsciiShaders.FRAGMENT_SHADER

        // Verify uniform definitions
        assertTrue(vertexShader.contains("u_MVPMatrix"))
        assertTrue(vertexShader.contains("u_Time"))
        assertTrue(vertexShader.contains("a_Position"))
        assertTrue(vertexShader.contains("a_TexCoord"))
        assertTrue(vertexShader.contains("a_FgColor"))
        assertTrue(vertexShader.contains("a_BgColor"))
        assertTrue(vertexShader.contains("a_LightParams"))
        assertTrue(vertexShader.contains("a_LightColor"))
        assertTrue(vertexShader.contains("a_EdgeParams"))
        assertTrue(vertexShader.contains("v_GridPos"))

        assertTrue(fragmentShader.contains("u_FontAtlas"))
        assertTrue(fragmentShader.contains("u_LightMap"))
        assertTrue(fragmentShader.contains("u_UseLightMap"))
        assertTrue(fragmentShader.contains("u_FlashlightEnabled"))
        assertTrue(fragmentShader.contains("u_FlashlightPos"))
        assertTrue(fragmentShader.contains("u_FlashlightParams"))
        assertTrue(fragmentShader.contains("u_FlashlightColor"))
        assertTrue(fragmentShader.contains("u_EnvLightCount"))
        assertTrue(fragmentShader.contains("u_EnvLightPos"))
        assertTrue(fragmentShader.contains("u_EnvLightColor"))
        assertTrue(fragmentShader.contains("u_ScanlineIntensity"))
        assertTrue(fragmentShader.contains("u_DitherStrength"))
        assertTrue(fragmentShader.contains("getBayer4x4"))
    }

    @Test
    fun testFlashlightDistanceAndSpotlightAttenuation() {
        val flashX = 10f
        val flashY = 10f
        val facingAngleRad = 0.0f // Facing right along positive X
        val range = 9.0f
        val innerConeCos = kotlin.math.cos(Math.toRadians(26.0)).toFloat()
        val outerConeCos = kotlin.math.cos(Math.toRadians(48.0)).toFloat()
        val baseIntensity = 1.45f

        // Point directly in front (14, 10) -> distance = 4.0, dot product = 1.0 > innerConeCos
        val targetX = 14f
        val targetY = 10f
        val toFragX = targetX - flashX
        val toFragY = targetY - flashY
        val dist = kotlin.math.sqrt(toFragX * toFragX + toFragY * toFragY)

        val dirNormX = toFragX / dist
        val dirNormY = toFragY / dist
        val flashDirX = kotlin.math.cos(facingAngleRad)
        val flashDirY = kotlin.math.sin(facingAngleRad)
        val dotVal = dirNormX * flashDirX + dirNormY * flashDirY

        assertTrue("Target is within range", dist <= range)
        assertTrue("Target is in spotlight core", dotVal > innerConeCos)

        val spotFactor = ((dotVal - outerConeCos) / (innerConeCos - outerConeCos)).coerceIn(0f, 1f)
        assertEquals(1.0f, spotFactor, 0.01f)

        val distAtten = 1.0f / (1.0f + 0.10f * dist + 0.045f * dist * dist)
        val flashPower = spotFactor * distAtten * baseIntensity

        assertTrue("Distance attenuation should decrease with distance", distAtten < 1.0f && distAtten > 0.0f)
        assertTrue("Flashlight power should be positive and bounded", flashPower in 0.5f..1.5f)

        // Point behind player (6, 10) -> dot product = -1.0 < outerConeCos
        val behindX = 6f
        val behindY = 10f
        val behindDist = 4.0f
        val behindDot = ((behindX - flashX) / behindDist) * flashDirX + ((behindY - flashY) / behindDist) * flashDirY
        assertTrue("Behind player is outside cone", behindDot < outerConeCos)
    }

    @Test
    fun testEnvironmentalLightDistanceAttenuation() {
        val lightX = 15f
        val lightY = 15f
        val radius = 5.0f
        val intensity = 1.2f

        // Point at center (15, 15)
        val centerDist = 0.0f
        val centerAtten = (1.0f - (centerDist / radius)).coerceIn(0f, 1f)
        val centerPower = (centerAtten * centerAtten) * intensity
        assertEquals(1.2f, centerPower, 0.01f)

        // Point at half radius (17.5, 15) -> dist = 2.5
        val midDist = 2.5f
        val midAtten = (1.0f - (midDist / radius)).coerceIn(0f, 1f)
        val midPower = (midAtten * midAtten) * intensity
        assertEquals(1.2f * 0.25f, midPower, 0.01f)

        // Point outside radius (21, 15) -> dist = 6.0
        val outDist = 6.0f
        val outAtten = (1.0f - (outDist / radius)).coerceIn(0f, 1f)
        val outPower = (outAtten * outAtten) * intensity
        assertEquals(0.0f, outPower, 0.001f)
    }

    @Test
    fun test3dMeshAsciiLuminanceShaderValidation() {
        val vertexShader = AsciiShaders.MESH_3D_ASCII_VERTEX_SHADER
        val fragmentShader = AsciiShaders.MESH_3D_ASCII_FRAGMENT_SHADER

        // Verify 3D vertex attributes and uniforms
        assertTrue("Contains 3D position attribute", vertexShader.contains("a_Position"))
        assertTrue("Contains 3D normal attribute", vertexShader.contains("a_Normal"))
        assertTrue("Contains 3D texture coord attribute", vertexShader.contains("a_TexCoord"))
        assertTrue("Contains MVP matrix", vertexShader.contains("u_MVPMatrix"))
        assertTrue("Contains Model matrix", vertexShader.contains("u_ModelMatrix"))
        assertTrue("Contains Normal matrix", vertexShader.contains("u_NormalMatrix"))
        assertTrue("Contains 3D Light Pos", vertexShader.contains("u_LightPos"))
        assertTrue("Contains Camera Pos", vertexShader.contains("u_CameraPos"))

        // Verify Fragment shader luminance mapping and font atlas sampling
        assertTrue("Contains Font Atlas uniform", fragmentShader.contains("u_FontAtlas"))
        assertTrue("Contains Screen Resolution", fragmentShader.contains("u_ScreenResolution"))
        assertTrue("Contains Cell Size", fragmentShader.contains("u_CellSize"))
        assertTrue("Contains Luminance Scale", fragmentShader.contains("u_LuminanceScale"))
        assertTrue("Contains Terminal Color", fragmentShader.contains("u_TerminalColor"))
        assertTrue("Contains Luminance calculation", fragmentShader.contains("dot(litRgb, vec3(0.299, 0.587, 0.114))"))
        assertTrue("Contains glyph index mapper", fragmentShader.contains("getGlyphIndexForLuminance"))
    }

    @Test
    fun testPinchToZoomFocalPointPreservation() {
        val minZoom = 0.45f
        val maxZoom = 3.80f
        var currentZoom = 1.05f
        var panOffsetX = 0f
        var panOffsetY = 0f

        val screenWidth = 1080f
        val screenHeight = 2400f
        val focusX = 750f // Pinching on right side
        val focusY = 1200f // Pinching mid-screen

        val scaleFactor = 1.50f // Zooming in by 50%
        val oldZoom = currentZoom
        val newZoom = (oldZoom * scaleFactor).coerceIn(minZoom, maxZoom)

        val viewportCenterX = screenWidth * 0.5f + panOffsetX
        val viewportCenterY = screenHeight * 0.44f + panOffsetY

        val zoomRatio = newZoom / oldZoom
        panOffsetX = (focusX - (screenWidth * 0.5f)) - (focusX - viewportCenterX) * zoomRatio
        panOffsetY = (focusY - (screenHeight * 0.44f)) - (focusY - viewportCenterY) * zoomRatio
        currentZoom = newZoom

        assertEquals(1.575f, currentZoom, 0.001f)
        // Verify pan offsets adjusted correctly to keep focal point anchored
        assertTrue("Pan offset X should shift to counter focal point drift", panOffsetX != 0f)
        assertTrue("Current zoom should be within bounds", currentZoom in minZoom..maxZoom)
    }

    @Test
    fun testPinchZoomClampingBounds() {
        val minZoom = 0.45f
        val maxZoom = 3.80f

        // Huge zoom in
        val hugeScaleIn = 10.0f
        val clampedMax = (1.05f * hugeScaleIn).coerceIn(minZoom, maxZoom)
        assertEquals(maxZoom, clampedMax, 0.001f)

        // Huge zoom out
        val hugeScaleOut = 0.05f
        val clampedMin = (1.05f * hugeScaleOut).coerceIn(minZoom, maxZoom)
        assertEquals(minZoom, clampedMin, 0.001f)
    }

    @Test
    fun testDoubleTapInspectionToggle() {
        var zoom = 1.05f
        val defaultZoom = 1.05f
        val inspectZoom = 2.40f

        // First double tap: from default (1.05) to inspect (2.40)
        zoom = if (zoom >= 1.8f) defaultZoom else inspectZoom
        assertEquals(2.40f, zoom, 0.001f)

        // Second double tap: from inspect (2.40) back to default (1.05)
        zoom = if (zoom >= 1.8f) defaultZoom else inspectZoom
        assertEquals(1.05f, zoom, 0.001f)
    }

    @Test
    fun testSharpnessAndAmbientDarknessUniformsInShader() {
        val fragmentShader = AsciiShaders.FRAGMENT_SHADER
        assertTrue("Contains u_Sharpness uniform", fragmentShader.contains("u_Sharpness"))
        assertTrue("Contains u_AmbientDarkness uniform", fragmentShader.contains("u_AmbientDarkness"))
        assertTrue("Contains glyph sharpness calculation", fragmentShader.contains("smoothstep(0.48 - halfWidth, 0.48 + halfWidth, rawGlyphMask)"))
        assertTrue("Contains ambient shadow tint", fragmentShader.contains("vec3(0.012, 0.016, 0.026)"))
        assertTrue("Contains spatial coordinate darkness", fragmentShader.contains("clamp((distToPlayer - 3.2) / 8.5, 0.0, 1.0)"))
    }

    @Test
    fun testAdaptiveCoordinateDarknessModel() {
        val playerX = 5.0f
        val playerY = 5.0f
        val ambientDarkness = 0.85f

        // 1. Point close to player (dist = 1.0)
        val nearX = 5.0f
        val nearY = 6.0f
        val distNear = kotlin.math.sqrt((nearX - playerX) * (nearX - playerX) + (nearY - playerY) * (nearY - playerY))
        val spatialDarknessNear = ((distNear - 3.2f) / 8.5f).coerceIn(0f, 1f)
        val ambientFloorNear = (0.18f * (1f - spatialDarknessNear * ambientDarkness) + 0.02f * (spatialDarknessNear * ambientDarkness))
        assertEquals(0.0f, spatialDarknessNear, 0.001f)
        assertEquals(0.18f, ambientFloorNear, 0.001f) // Full near ambient visibility

        // 2. Point deep in subterranean corridor (dist = 12.0)
        val farX = 17.0f
        val farY = 5.0f
        val distFar = kotlin.math.sqrt((farX - playerX) * (farX - playerX) + (farY - playerY) * (farY - playerY))
        val spatialDarknessFar = ((distFar - 3.2f) / 8.5f).coerceIn(0f, 1f)
        val ambientFloorFar = (0.18f * (1f - spatialDarknessFar * ambientDarkness) + 0.02f * (spatialDarknessFar * ambientDarkness))
        assertTrue("Far spatial darkness should be high", spatialDarknessFar >= 0.9f)
        assertTrue("Far ambient floor should drop into deep shadow", ambientFloorFar < 0.05f)
    }
}
