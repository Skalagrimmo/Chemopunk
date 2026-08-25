package com.example.engine

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.Player
import com.example.data.TileType
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * OpenGL ES 2.0 Renderer for the 2.5D Isometric ASCII Wasteland Viewport.
 *
 * Implements GPU-accelerated:
 * - Dynamic vertex transformation with 2.5D isometric elevation projection
 * - Font Atlas glyph alpha compositing
 * - Real-time multi-source dynamic photon lighting
 * - Hardware spatial Bayer dither & CRT scanline shader passes
 */
class AsciiGlRenderer : GLSurfaceView.Renderer {

    companion object {
        private const val TAG = "AsciiGlRenderer"
    }

    // Thread-safe game state snapshot
    data class RenderSnapshot(
        val mapGrid: List<List<TileType>> = emptyList(),
        val player: Player = Player(),
        val enemies: List<Enemy> = emptyList(),
        val lightSources: List<LightSource> = emptyList(),
        val selectedTile: Pair<Int, Int>? = null,
        val discoveredTiles: Set<Pair<Int, Int>> = emptySet(),
        val floatingTexts: List<FloatingText> = emptyList(),
        val paletteIndex: Int = 0,
        val panOffsetX: Float = 0f,
        val panOffsetY: Float = 0f,
        val zoomLevel: Float = 1.05f,
        val scanlineIntensity: Float = 0.55f,
        val ditherStrength: Float = 1.0f
    )

    private val stateLock = Any()
    private var pendingSnapshot = RenderSnapshot()
    private var currentSnapshot = RenderSnapshot()

    // OpenGL Matrices & Shader Handles
    private var programId = 0
    private var fontAtlasTextureId = 0

    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)

    private var uMVPMatrixLoc = -1
    private var uTimeLoc = -1
    private var uViewportSizeLoc = -1
    private var uFontAtlasLoc = -1
    private var uLightMapLoc = -1
    private var uUseLightMapLoc = -1
    private var uScanlineIntensityLoc = -1
    private var uDitherStrengthLoc = -1
    private var uResolutionLoc = -1

    // Custom Ramp & Edge Isolation Uniform Locations
    private var uEdgeIsolationStrengthLoc = -1
    private var uRampQuantizationLoc = -1
    private var uRampHighlightTintLoc = -1
    private var uRampShadowTintLoc = -1
    private var uEdgeTintLoc = -1

    // Sharpness and Ambient Darkness Uniform Locations
    private var uSharpnessLoc = -1
    private var uAmbientDarknessLoc = -1

    // Shader-Based Light Map & Distance Lighting Uniform Locations
    private var uMapDimensionsLoc = -1
    private var uFlashlightEnabledLoc = -1
    private var uFlashlightPosLoc = -1
    private var uFlashlightParamsLoc = -1
    private var uFlashlightColorLoc = -1
    private var uEnvLightCountLoc = -1
    private var uEnvLightPosLoc = -1
    private var uEnvLightColorLoc = -1

    private var aPositionLoc = -1
    private var aTexCoordLoc = -1
    private var aFgColorLoc = -1
    private var aBgColorLoc = -1
    private var aLightParamsLoc = -1
    private var aLightColorLoc = -1
    private var aEdgeParamsLoc = -1

    private var surfaceWidth = 1080
    private var surfaceHeight = 1920
    private val startTimeMs = System.currentTimeMillis()

    // Lighting Engine and Buffers
    private val lightingEngine = DynamicLightingEngine()
    private val lightMapBuffer = LightMapBuffer(width = 32, height = 32, lightingEngine = lightingEngine)
    val dynamicLightMapSystem = GlDynamicLightMapSystem(mapWidth = 32, mapHeight = 32)
    private val characterBuffer = AsciiCharacterBuffer(maxQuads = 12288)
    private val activeLightsBuffer = mutableListOf<LightSource>()
    val mesh3dRenderer = Ascii3dMeshRenderer()

    fun updateState(snapshot: RenderSnapshot) {
        synchronized(stateLock) {
            pendingSnapshot = snapshot
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // Compile and link custom OpenGL ES 2.0 Shaders
        programId = AsciiShaders.createProgram()
        if (programId == 0) {
            Log.e(TAG, "Failed to create OpenGL ES 2.0 Shader Program")
            return
        }

        // Cache uniform and attribute locations
        uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "u_MVPMatrix")
        uTimeLoc = GLES20.glGetUniformLocation(programId, "u_Time")
        uViewportSizeLoc = GLES20.glGetUniformLocation(programId, "u_ViewportSize")
        uFontAtlasLoc = GLES20.glGetUniformLocation(programId, "u_FontAtlas")
        uLightMapLoc = GLES20.glGetUniformLocation(programId, "u_LightMap")
        uUseLightMapLoc = GLES20.glGetUniformLocation(programId, "u_UseLightMap")
        uScanlineIntensityLoc = GLES20.glGetUniformLocation(programId, "u_ScanlineIntensity")
        uDitherStrengthLoc = GLES20.glGetUniformLocation(programId, "u_DitherStrength")
        uResolutionLoc = GLES20.glGetUniformLocation(programId, "u_Resolution")
        uMapDimensionsLoc = GLES20.glGetUniformLocation(programId, "u_MapDimensions")

        uSharpnessLoc = GLES20.glGetUniformLocation(programId, "u_Sharpness")
        uAmbientDarknessLoc = GLES20.glGetUniformLocation(programId, "u_AmbientDarkness")

        uEdgeIsolationStrengthLoc = GLES20.glGetUniformLocation(programId, "u_EdgeIsolationStrength")
        uRampQuantizationLoc = GLES20.glGetUniformLocation(programId, "u_RampQuantization")
        uRampHighlightTintLoc = GLES20.glGetUniformLocation(programId, "u_RampHighlightTint")
        uRampShadowTintLoc = GLES20.glGetUniformLocation(programId, "u_RampShadowTint")
        uEdgeTintLoc = GLES20.glGetUniformLocation(programId, "u_EdgeTint")

        uFlashlightEnabledLoc = GLES20.glGetUniformLocation(programId, "u_FlashlightEnabled")
        uFlashlightPosLoc = GLES20.glGetUniformLocation(programId, "u_FlashlightPos")
        uFlashlightParamsLoc = GLES20.glGetUniformLocation(programId, "u_FlashlightParams")
        uFlashlightColorLoc = GLES20.glGetUniformLocation(programId, "u_FlashlightColor")
        uEnvLightCountLoc = GLES20.glGetUniformLocation(programId, "u_EnvLightCount")
        uEnvLightPosLoc = GLES20.glGetUniformLocation(programId, "u_EnvLightPos")
        uEnvLightColorLoc = GLES20.glGetUniformLocation(programId, "u_EnvLightColor")

        aPositionLoc = GLES20.glGetAttribLocation(programId, "a_Position")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "a_TexCoord")
        aFgColorLoc = GLES20.glGetAttribLocation(programId, "a_FgColor")
        aBgColorLoc = GLES20.glGetAttribLocation(programId, "a_BgColor")
        aLightParamsLoc = GLES20.glGetAttribLocation(programId, "a_LightParams")
        aLightColorLoc = GLES20.glGetAttribLocation(programId, "a_LightColor")
        aEdgeParamsLoc = GLES20.glGetAttribLocation(programId, "a_EdgeParams")

        // Generate 256x256 Font Atlas texture
        fontAtlasTextureId = FontAtlasGenerator.createFontAtlasTexture()

        // Initialize GPU dynamic light map texture
        dynamicLightMapSystem.initGl()

        // Initialize 3D Mesh to 2D ASCII Luminance Shader Pipeline
        mesh3dRenderer.init()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)

        // Setup 2D Screen Orthographic Projection Matrix: (0,0) top-left to (width, height) bottom-right
        Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -100f, 100f)
        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        synchronized(stateLock) {
            currentSnapshot = pendingSnapshot
        }

        val animTime = (System.currentTimeMillis() - startTimeMs) / 1000f

        // Clear Screen with Palette Background Color
        val (bgR, bgG, bgB) = getPaletteClearColor(currentSnapshot.paletteIndex)
        GLES20.glClearColor(bgR, bgG, bgB, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        if (programId == 0 || fontAtlasTextureId == 0) return

        GLES20.glUseProgram(programId)

        // Upload Standard Uniforms
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniform1f(uTimeLoc, animTime)
        GLES20.glUniform2f(uViewportSizeLoc, surfaceWidth.toFloat(), surfaceHeight.toFloat())
        GLES20.glUniform1f(uScanlineIntensityLoc, currentSnapshot.scanlineIntensity)
        GLES20.glUniform1f(uDitherStrengthLoc, currentSnapshot.ditherStrength)
        GLES20.glUniform2f(uResolutionLoc, surfaceWidth.toFloat(), surfaceHeight.toFloat())

        // Upload Custom Ramp & Edge Isolation Uniforms
        val rampType = CustomRampEngine.RampType.fromIndex(currentSnapshot.paletteIndex)
        val (hiR, hiG, hiB) = rampType.highlightTint
        val (shR, shG, shB) = rampType.shadowTint
        val (edR, edG, edB) = rampType.edgeTint

        if (uEdgeIsolationStrengthLoc != -1) {
            GLES20.glUniform1f(uEdgeIsolationStrengthLoc, 1.0f)
        }
        if (uRampQuantizationLoc != -1) {
            GLES20.glUniform1f(uRampQuantizationLoc, rampType.quantizationSteps)
        }
        if (uRampHighlightTintLoc != -1) {
            GLES20.glUniform3f(uRampHighlightTintLoc, hiR, hiG, hiB)
        }
        if (uRampShadowTintLoc != -1) {
            GLES20.glUniform3f(uRampShadowTintLoc, shR, shG, shB)
        }
        if (uEdgeTintLoc != -1) {
            GLES20.glUniform3f(uEdgeTintLoc, edR, edG, edB)
        }

        // Upload Sharpness & Adaptive Ambient Darkness Uniforms
        if (uSharpnessLoc != -1) {
            GLES20.glUniform1f(uSharpnessLoc, 1.85f)
        }
        if (uAmbientDarknessLoc != -1) {
            GLES20.glUniform1f(uAmbientDarknessLoc, 0.85f)
        }

        // Bind Font Atlas Texture to Texture Unit 0
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fontAtlasTextureId)
        GLES20.glUniform1i(uFontAtlasLoc, 0)

        // Bind Dynamic LightMap Texture to Texture Unit 1
        if (dynamicLightMapSystem.textureId != 0 && uLightMapLoc != -1) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, dynamicLightMapSystem.textureId)
            GLES20.glUniform1i(uLightMapLoc, 1)
            if (uUseLightMapLoc != -1) {
                GLES20.glUniform1i(uUseLightMapLoc, 1)
            }
        } else if (uUseLightMapLoc != -1) {
            GLES20.glUniform1i(uUseLightMapLoc, 0)
        }

        // Build 2.5D Isometric World Quads into Double-Buffered Vertex Stream
        buildWorldGeometry(animTime)

        // Upload LightMap & Distance Lighting Uniforms
        val mapGrid = currentSnapshot.mapGrid
        val mapRows = if (mapGrid.isNotEmpty()) mapGrid.size.toFloat() else 32.0f
        val mapCols = if (mapGrid.isNotEmpty() && mapGrid[0].isNotEmpty()) mapGrid[0].size.toFloat() else 32.0f

        if (uMapDimensionsLoc != -1) {
            GLES20.glUniform2f(uMapDimensionsLoc, mapCols, mapRows)
        }

        // Upload Flashlight Uniforms
        val player = currentSnapshot.player
        val facingRad = Math.toRadians(player.angleDegrees.toDouble()).toFloat()
        val innerConeCos = kotlin.math.cos(Math.toRadians(26.0)).toFloat()
        val outerConeCos = kotlin.math.cos(Math.toRadians(48.0)).toFloat()
        val flicker = (kotlin.math.sin(animTime * 14.5f) * 0.04f + kotlin.math.sin(animTime * 28.0f) * 0.02f)

        if (uFlashlightEnabledLoc != -1) {
            GLES20.glUniform1i(uFlashlightEnabledLoc, 1)
        }
        if (uFlashlightPosLoc != -1) {
            GLES20.glUniform3f(uFlashlightPosLoc, player.x, player.y, facingRad)
        }
        if (uFlashlightParamsLoc != -1) {
            GLES20.glUniform4f(uFlashlightParamsLoc, 9.0f, innerConeCos, outerConeCos, 1.45f)
        }
        if (uFlashlightColorLoc != -1) {
            GLES20.glUniform4f(uFlashlightColorLoc, 0.90f, 0.96f, 1.0f, flicker)
        }

        // Upload Environmental Lights Uniforms (up to 6 lights)
        val envPosArray = FloatArray(24)
        val envColorArray = FloatArray(24)
        var envLightCount = 0

        for (light in activeLightsBuffer) {
            if (light.type != LightType.PLAYER_FLASHLIGHT && envLightCount < 6) {
                val idx = envLightCount * 4
                envPosArray[idx] = light.gridX
                envPosArray[idx + 1] = light.gridY
                envPosArray[idx + 2] = light.radius
                envPosArray[idx + 3] = light.intensity

                envColorArray[idx] = light.colorR / 255f
                envColorArray[idx + 1] = light.colorG / 255f
                envColorArray[idx + 2] = light.colorB / 255f
                envColorArray[idx + 3] = light.pulseSpeed

                envLightCount++
            }
        }

        if (uEnvLightCountLoc != -1) {
            GLES20.glUniform1i(uEnvLightCountLoc, envLightCount)
        }
        if (uEnvLightPosLoc != -1 && envLightCount > 0) {
            GLES20.glUniform4fv(uEnvLightPosLoc, 6, envPosArray, 0)
        }
        if (uEnvLightColorLoc != -1 && envLightCount > 0) {
            GLES20.glUniform4fv(uEnvLightColorLoc, 6, envColorArray, 0)
        }

        val vertexCount = characterBuffer.vertexCount
        if (vertexCount == 0) return

        val floatBuffer = characterBuffer.floatBuffer

        // Bind Vertex Attributes
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        floatBuffer.position(AsciiCharacterBuffer.POS_OFFSET)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, AsciiCharacterBuffer.VERTEX_STRIDE, floatBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        floatBuffer.position(AsciiCharacterBuffer.TEX_OFFSET)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, AsciiCharacterBuffer.VERTEX_STRIDE, floatBuffer)

        GLES20.glEnableVertexAttribArray(aFgColorLoc)
        floatBuffer.position(AsciiCharacterBuffer.FG_OFFSET)
        GLES20.glVertexAttribPointer(aFgColorLoc, 4, GLES20.GL_FLOAT, false, AsciiCharacterBuffer.VERTEX_STRIDE, floatBuffer)

        GLES20.glEnableVertexAttribArray(aBgColorLoc)
        floatBuffer.position(AsciiCharacterBuffer.BG_OFFSET)
        GLES20.glVertexAttribPointer(aBgColorLoc, 4, GLES20.GL_FLOAT, false, AsciiCharacterBuffer.VERTEX_STRIDE, floatBuffer)

        GLES20.glEnableVertexAttribArray(aLightParamsLoc)
        floatBuffer.position(AsciiCharacterBuffer.LIGHT_PARAMS_OFFSET)
        GLES20.glVertexAttribPointer(aLightParamsLoc, 4, GLES20.GL_FLOAT, false, AsciiCharacterBuffer.VERTEX_STRIDE, floatBuffer)

        GLES20.glEnableVertexAttribArray(aLightColorLoc)
        floatBuffer.position(AsciiCharacterBuffer.LIGHT_COLOR_OFFSET)
        GLES20.glVertexAttribPointer(aLightColorLoc, 3, GLES20.GL_FLOAT, false, AsciiCharacterBuffer.VERTEX_STRIDE, floatBuffer)

        if (aEdgeParamsLoc != -1) {
            GLES20.glEnableVertexAttribArray(aEdgeParamsLoc)
            floatBuffer.position(AsciiCharacterBuffer.EDGE_PARAMS_OFFSET)
            GLES20.glVertexAttribPointer(aEdgeParamsLoc, 4, GLES20.GL_FLOAT, false, AsciiCharacterBuffer.VERTEX_STRIDE, floatBuffer)
        }

        // Execute GPU Draw Call
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)

        // Cleanup Attribute Pointers
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        GLES20.glDisableVertexAttribArray(aFgColorLoc)
        GLES20.glDisableVertexAttribArray(aBgColorLoc)
        GLES20.glDisableVertexAttribArray(aLightParamsLoc)
        GLES20.glDisableVertexAttribArray(aLightColorLoc)
        if (aEdgeParamsLoc != -1) {
            GLES20.glDisableVertexAttribArray(aEdgeParamsLoc)
        }
    }

    private fun buildWorldGeometry(animTime: Float) {
        characterBuffer.beginWrite()

        val snap = currentSnapshot
        val mapGrid = snap.mapGrid
        if (mapGrid.isEmpty()) {
            characterBuffer.finishWrite()
            return
        }

        val rows = mapGrid.size
        val cols = mapGrid[0].size
        val player = snap.player
        val zoom = snap.zoomLevel
        val centerX = surfaceWidth * 0.5f + snap.panOffsetX
        val centerY = surfaceHeight * 0.44f + snap.panOffsetY

        // Assemble active light sources
        activeLightsBuffer.clear()
        activeLightsBuffer.add(
            LightSource(
                id = "player_torch",
                gridX = player.x,
                gridY = player.y,
                elevation = 0.5f,
                colorR = 210,
                colorG = 240,
                colorB = 255,
                intensity = 1.35f,
                radius = 8.5f,
                type = LightType.PLAYER_FLASHLIGHT,
                isDirectional = true,
                directionAngleDeg = player.angleDegrees,
                coneAngleDeg = 75f,
                flickerFrequency = 7.0f,
                flickerIntensity = 0.06f
            )
        )
        activeLightsBuffer.addAll(snap.lightSources)

        // Environmental light sources
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                when (mapGrid[r][c]) {
                    TileType.TOXIC_POOL -> {
                        activeLightsBuffer.add(
                            LightSource(
                                id = "toxic_${c}_${r}",
                                gridX = c + 0.5f,
                                gridY = r + 0.5f,
                                colorR = 40,
                                colorG = 255,
                                colorB = 90,
                                intensity = 1.05f,
                                radius = 3.2f,
                                type = LightType.BIO_LUMINESCENT,
                                flickerFrequency = 5.0f,
                                flickerIntensity = 0.18f,
                                pulseSpeed = 4.5f
                            )
                        )
                    }
                    TileType.EXTRACTION_LIFT -> {
                        activeLightsBuffer.add(
                            LightSource(
                                id = "lift_${c}_${r}",
                                gridX = c + 0.5f,
                                gridY = r + 0.5f,
                                colorR = 255,
                                colorG = 220,
                                colorB = 60,
                                intensity = 1.4f,
                                radius = 4.8f,
                                type = LightType.EXTRACTION_BEACON,
                                pulseSpeed = 6.0f
                            )
                        )
                    }
                    else -> {}
                }
            }
        }

        // Update GPU Dynamic Light-Map Subsystem
        dynamicLightMapSystem.updateAndUpload(
            mapGrid = mapGrid,
            player = player,
            enemies = snap.enemies,
            externalLights = activeLightsBuffer,
            discoveredTiles = snap.discoveredTiles,
            animTime = animTime
        )

        // Compute spatial light map buffer
        lightMapBuffer.computeLightMap(
            mapGrid = mapGrid,
            lightSources = activeLightsBuffer,
            discoveredTiles = snap.discoveredTiles,
            animTime = animTime,
            enableShadows = true
        )

        // Draw Engagement Radius (Dotted ring)
        renderEngagementRing(centerX, centerY, player.x, player.y, zoom, snap.paletteIndex)

        // Depth sorted 2.5D Isometric Rendering (r + c from back to front)
        val maxDepth = rows + cols
        for (depth in 0..maxDepth) {
            for (r in 0 until rows) {
                val c = depth - r
                if (c in 0 until cols) {
                    val tile = mapGrid[r][c]

                    val elevation = if (tile == TileType.WALL) 0.5f else 0f
                    val lighting = lightMapBuffer.getTileLighting(c, r, elevation)

                    if (lighting.isFOWHidden) {
                        val (isoX, isoY) = characterBuffer.gridToIso(c.toFloat(), r.toFloat(), 0f, centerX, centerY, player.x, player.y, zoom)
                        if (isoX in -40f..surfaceWidth + 40f && isoY in -40f..surfaceHeight + 40f) {
                            characterBuffer.pushCharCell(
                                cx = isoX, cy = isoY,
                                halfW = 4f * zoom, halfH = 4f * zoom, z = 0f,
                                char = '.',
                                fgR = 0.12f, fgG = 0.16f, fgB = 0.22f, fgA = 0.35f
                            )
                        }
                        continue
                    }

                    // Render 2.5D Isometric Tile
                    val isSelected = (snap.selectedTile?.first == c && snap.selectedTile?.second == r)
                    renderIsoTileGl(tile, c, r, centerX, centerY, player.x, player.y, zoom, lighting, isSelected, animTime, snap.paletteIndex)

                    // Sharp Edge Isolation & Contours for Walls & Hazard Pools
                    if (tile == TileType.WALL || tile == TileType.TOXIC_POOL) {
                        val edges = SharpEdgeIsolationEngine.extractTileEdges(mapGrid, c, r)
                        val rampType = CustomRampEngine.RampType.fromIndex(snap.paletteIndex)
                        val (edR, edG, edB) = rampType.edgeTint

                        edges.forEach { edge ->
                            val (edgeIsoX, edgeIsoY) = characterBuffer.gridToIso(
                                edge.gridX, edge.gridY, edge.elevation,
                                centerX, centerY, player.x, player.y, zoom
                            )
                            if (edgeIsoX in -40f..surfaceWidth + 40f && edgeIsoY in -40f..surfaceHeight + 40f) {
                                characterBuffer.pushCharCell(
                                    cx = edgeIsoX,
                                    cy = edgeIsoY,
                                    halfW = 12f * zoom,
                                    halfH = 12f * zoom,
                                    z = edge.elevation + 0.04f,
                                    char = edge.char,
                                    fgR = edR, fgG = edG, fgB = edB, fgA = 0.95f,
                                    lightIntensity = lighting.totalIntensity * 1.25f,
                                    edgeFlag = 1.0f,
                                    edgeStrength = edge.isolationStrength
                                )
                            }
                        }
                    }

                    // Sharp Edge Isolation for Target Selection Reticle
                    if (isSelected) {
                        val reticleEdges = SharpEdgeIsolationEngine.getSelectionReticleEdges(c, r)
                        reticleEdges.forEach { edge ->
                            val (retIsoX, retIsoY) = characterBuffer.gridToIso(
                                edge.gridX, edge.gridY, 0.1f,
                                centerX, centerY, player.x, player.y, zoom
                            )
                            characterBuffer.pushCharCell(
                                cx = retIsoX + edge.normalX * 14f * zoom,
                                cy = retIsoY,
                                halfW = 10f * zoom,
                                halfH = 12f * zoom,
                                z = 0.1f,
                                char = edge.char,
                                fgR = 1.0f, fgG = 0.85f, fgB = 0.2f, fgA = 1.0f,
                                lightIntensity = 1.6f,
                                edgeFlag = 1.0f,
                                edgeStrength = 1.5f,
                                glow = 1.0f
                            )
                        }
                    }

                    // Render Enemies on this tile
                    snap.enemies.filter { it.isAlive && it.x.toInt() == c && it.y.toInt() == r }.forEach { enemy ->
                        val enemyLighting = lightMapBuffer.sampleInterpolatedLighting(
                            gridX = enemy.x,
                            gridY = enemy.y,
                            elevation = 0.42f
                        )
                        if (!enemyLighting.isFOWHidden) {
                            renderEnemyGl(enemy, centerX, centerY, player.x, player.y, zoom, enemyLighting, isSelected, animTime, snap.paletteIndex)
                        }
                    }

                    // Render Player Character
                    if (player.x.toInt() == c && player.y.toInt() == r) {
                        val playerLighting = lightMapBuffer.sampleInterpolatedLighting(
                            gridX = player.x,
                            gridY = player.y,
                            elevation = 0.42f
                        )
                        renderPlayerGl(player, centerX, centerY, zoom, playerLighting, animTime, snap.paletteIndex)
                    }
                }
            }
        }

        // Render Floating Damage/Telemetry Texts
        val curTime = System.currentTimeMillis()
        snap.floatingTexts.forEach { ft ->
            val age = curTime - ft.spawnTime
            if (age < ft.durationMs) {
                val progress = age.toFloat() / ft.durationMs.toFloat()
                val elevationOffset = progress * 1.8f
                val (ftX, ftY) = characterBuffer.gridToIso(ft.x, ft.y, elevationOffset, centerX, centerY, player.x, player.y, zoom)
                val alpha = (1.0f - progress).coerceIn(0f, 1f)

                val colInt = ft.colorHex.toInt()
                val rF = ((colInt shr 16) and 0xFF) / 255f
                val gF = ((colInt shr 8) and 0xFF) / 255f
                val bF = (colInt and 0xFF) / 255f

                characterBuffer.pushText(
                    startX = ftX - (ft.text.length * 5.5f * zoom),
                    startY = ftY - 30f * zoom,
                    text = ft.text,
                    charW = 11f * zoom,
                    charH = 15f * zoom,
                    z = 1.0f,
                    fgR = rF, fgG = gF, fgB = bF, fgA = alpha
                )
            }
        }

        // Finalize geometry back-buffer and atomic swap to front buffer
        characterBuffer.finishWrite()
    }

    private fun renderIsoTileGl(
        tile: TileType,
        gridX: Int,
        gridY: Int,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float,
        lighting: TileLighting,
        isSelected: Boolean,
        animTime: Float,
        palette: Int
    ) {
        val (baseX, baseY) = characterBuffer.gridToIso(gridX.toFloat(), gridY.toFloat(), 0f, centerX, centerY, camX, camY, zoom)
        if (baseX < -60f || baseX > surfaceWidth + 60f || baseY < -60f || baseY > surfaceHeight + 60f) return

        val lightR = lighting.colorR / 255f
        val lightG = lighting.colorG / 255f
        val lightB = lighting.colorB / 255f
        val lightInt = lighting.totalIntensity

        val halfW = 20f * zoom
        val halfH = 14f * zoom

        val gx = gridX.toFloat()
        val gy = gridY.toFloat()

        when (tile) {
            TileType.WALL -> {
                val (topX, topY) = characterBuffer.gridToIso(gx, gy, 1.0f, centerX, centerY, camX, camY, zoom)

                // Wall Base / Pillars
                characterBuffer.pushCharCell(
                    cx = baseX, cy = baseY,
                    halfW = halfW, halfH = halfH, z = 0f,
                    char = '#',
                    fgR = 0.35f, fgG = 0.38f, fgB = 0.45f, fgA = 1.0f,
                    bgR = 0.08f, bgG = 0.09f, bgB = 0.12f, bgA = 0.9f,
                    lightIntensity = lightInt * 0.75f,
                    tintR = lightR, tintG = lightG, tintB = lightB,
                    gridX = gx, gridY = gy
                )

                // Wall Top Facet
                characterBuffer.pushCharCell(
                    cx = topX, cy = topY,
                    halfW = halfW, halfH = halfH, z = 1.0f,
                    char = '#',
                    fgR = 0.65f, fgG = 0.70f, fgB = 0.85f, fgA = 1.0f,
                    bgR = 0.15f, bgG = 0.18f, bgB = 0.24f, bgA = 0.95f,
                    lightIntensity = lightInt,
                    tintR = lightR, tintG = lightG, tintB = lightB,
                    gridX = gx, gridY = gy
                )
            }
            TileType.FLOOR -> {
                // Adaptive Wall Ambient Occlusion Shadow on floor adjacent to walls
                val map = currentSnapshot.mapGrid
                val hasNorthWall = gridY > 0 && gridY - 1 < map.size && map[gridY - 1].getOrNull(gridX) == TileType.WALL
                val hasWestWall = gridX > 0 && gridY < map.size && map[gridY].getOrNull(gridX - 1) == TileType.WALL
                val hasWallContact = hasNorthWall || hasWestWall

                val char = if (isSelected) '+' else '.'
                val (fgR, fgG, fgB) = if (isSelected) {
                    Triple(1.0f, 0.85f, 0.2f)
                } else if (hasWallContact) {
                    Triple(0.25f, 0.28f, 0.35f) // Darker ambient contact shadow near wall
                } else {
                    Triple(0.40f, 0.45f, 0.52f)
                }
                val bgA = if (isSelected) 0.35f else 0.0f
                val effectiveLight = if (hasWallContact) lightInt * 0.72f else lightInt

                characterBuffer.pushCharCell(
                    cx = baseX, cy = baseY,
                    halfW = halfW, halfH = halfH, z = 0f,
                    char = char,
                    fgR = fgR, fgG = fgG, fgB = fgB, fgA = 0.85f,
                    bgR = 0.9f, bgG = 0.7f, bgB = 0.1f, bgA = bgA,
                    lightIntensity = effectiveLight,
                    dither = 1.0f,
                    tintR = lightR, tintG = lightG, tintB = lightB,
                    gridX = gx, gridY = gy
                )
            }
            TileType.TOXIC_POOL -> {
                // Animated chemical fluid wave
                val pulse = sin(animTime * 4.0f + gridX + gridY) * 0.15f + 0.85f
                characterBuffer.pushCharCell(
                    cx = baseX, cy = baseY,
                    halfW = halfW, halfH = halfH, z = 0f,
                    char = '~',
                    fgR = 0.15f, fgG = 1.0f, fgB = 0.35f, fgA = 1.0f,
                    bgR = 0.05f, bgG = 0.25f, bgB = 0.08f, bgA = 0.75f,
                    lightIntensity = lightInt * pulse,
                    glow = 1.0f,
                    wave = 1.0f,
                    tintR = 0.2f, tintG = 1.0f, tintB = 0.4f,
                    gridX = gx, gridY = gy
                )
            }
            TileType.EXTRACTION_LIFT -> {
                characterBuffer.pushCharCell(
                    cx = baseX, cy = baseY,
                    halfW = halfW, halfH = halfH, z = 0.1f,
                    char = '=',
                    fgR = 1.0f, fgG = 0.90f, fgB = 0.25f, fgA = 1.0f,
                    bgR = 0.25f, bgG = 0.20f, bgB = 0.05f, bgA = 0.85f,
                    lightIntensity = lightInt,
                    glow = 1.0f,
                    tintR = 1.0f, tintG = 0.9f, tintB = 0.3f,
                    gridX = gx, gridY = gy
                )
            }
            TileType.DOOR -> {
                characterBuffer.pushCharCell(
                    cx = baseX, cy = baseY,
                    halfW = halfW, halfH = halfH, z = 0.5f,
                    char = '+',
                    fgR = 0.85f, fgG = 0.65f, fgB = 0.25f, fgA = 1.0f,
                    bgR = 0.18f, bgG = 0.12f, bgB = 0.05f, bgA = 0.8f,
                    lightIntensity = lightInt,
                    tintR = lightR, tintG = lightG, tintB = lightB,
                    gridX = gx, gridY = gy
                )
            }
        }
    }

    private fun renderPlayerGl(
        player: Player,
        centerX: Float,
        centerY: Float,
        zoom: Float,
        lighting: TileLighting,
        animTime: Float,
        palette: Int
    ) {
        val (isoX, isoY) = characterBuffer.gridToIso(player.x, player.y, 0.45f, centerX, centerY, player.x, player.y, zoom)

        // Contact Ground Shadow
        val (shadowX, shadowY) = characterBuffer.gridToIso(player.x, player.y, 0.02f, centerX, centerY, player.x, player.y, zoom)
        characterBuffer.pushCharCell(
            cx = shadowX, cy = shadowY + 6f * zoom,
            halfW = 16f * zoom, halfH = 8f * zoom, z = 0.02f,
            char = '*',
            fgR = 0.0f, fgG = 0.0f, fgB = 0.0f, fgA = 0.45f
        )

        // Player ASCII Sprite '@'
        val lightR = lighting.colorR / 255f
        val lightG = lighting.colorG / 255f
        val lightB = lighting.colorB / 255f

        characterBuffer.pushCharCell(
            cx = isoX, cy = isoY - 6f * zoom,
            halfW = 16f * zoom, halfH = 20f * zoom, z = 0.45f,
            char = '@',
            fgR = 0.31f, fgG = 0.82f, fgB = 0.77f, fgA = 1.0f, // Immersive Teal
            bgR = 0.05f, bgG = 0.15f, bgB = 0.14f, bgA = 0.75f,
            lightIntensity = 1.25f,
            glow = 1.0f,
            tintR = lightR, tintG = lightG, tintB = lightB,
            gridX = player.x, gridY = player.y
        )

        // Directional Flashlight Cone Ray in Front of Player
        val rad = Math.toRadians(player.angleDegrees.toDouble())
        val coneTargetX = player.x + cos(rad).toFloat() * 1.5f
        val coneTargetY = player.y + sin(rad).toFloat() * 1.5f
        val (coneIsoX, coneIsoY) = characterBuffer.gridToIso(coneTargetX, coneTargetY, 0.2f, centerX, centerY, player.x, player.y, zoom)

        characterBuffer.pushCharCell(
            cx = coneIsoX, cy = coneIsoY,
            halfW = 8f * zoom, halfH = 8f * zoom, z = 0.2f,
            char = '>',
            fgR = 0.85f, fgG = 0.95f, fgB = 1.0f, fgA = 0.65f,
            lightIntensity = 1.5f,
            tintR = 0.85f, tintG = 0.95f, tintB = 1.0f,
            gridX = coneTargetX, gridY = coneTargetY
        )
    }

    private fun renderEnemyGl(
        enemy: Enemy,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float,
        lighting: TileLighting,
        isSelected: Boolean,
        animTime: Float,
        palette: Int
    ) {
        val (isoX, isoY) = characterBuffer.gridToIso(enemy.x, enemy.y, 0.42f, centerX, centerY, camX, camY, zoom)
        if (isoX < -40f || isoX > surfaceWidth + 40f || isoY < -40f || isoY > surfaceHeight + 40f) return

        // Directional Contact Ground Shadow beneath Enemy
        val (shadowX, shadowY) = characterBuffer.gridToIso(enemy.x, enemy.y, 0.02f, centerX, centerY, camX, camY, zoom)
        characterBuffer.pushCharCell(
            cx = shadowX, cy = shadowY + 6f * zoom,
            halfW = 15f * zoom, halfH = 7f * zoom, z = 0.02f,
            char = '*',
            fgR = 0.0f, fgG = 0.0f, fgB = 0.0f, fgA = 0.40f
        )

        val lightR = lighting.colorR / 255f
        val lightG = lighting.colorG / 255f
        val lightB = lighting.colorB / 255f
        val lightInt = lighting.totalIntensity

        val char = enemy.asciiGlyph

        val (fgR, fgG, fgB) = Triple(0.92f, 0.35f, 0.25f) // Wasteland Hostile Red

        characterBuffer.pushCharCell(
            cx = isoX, cy = isoY - 6f * zoom,
            halfW = 16f * zoom,
            halfH = 18f * zoom,
            z = 0.42f,
            char = char,
            fgR = fgR, fgG = fgG, fgB = fgB, fgA = 1.0f,
            bgR = if (isSelected) 0.5f else 0.15f,
            bgG = if (isSelected) 0.1f else 0.02f,
            bgB = if (isSelected) 0.1f else 0.02f,
            bgA = if (isSelected) 0.85f else 0.6f,
            lightIntensity = lightInt,
            glow = if (isSelected) 1.0f else 0.0f,
            tintR = lightR, tintG = lightG, tintB = lightB,
            gridX = enemy.x, gridY = enemy.y
        )

        // Health Bar Indicator above Enemy
        val healthPct = (enemy.hp.toFloat() / enemy.maxHp.toFloat()).coerceIn(0f, 1f)
        val barW = 28f * zoom
        val barH = 4f * zoom
        val barY = isoY - 26f * zoom

        characterBuffer.pushCharCell(
            cx = isoX, cy = barY,
            halfW = barW * 0.5f, halfH = barH * 0.5f, z = 0.5f,
            char = '=',
            fgR = if (healthPct > 0.4f) 0.2f else 0.9f,
            fgG = if (healthPct > 0.4f) 0.9f else 0.2f,
            fgB = 0.2f,
            fgA = 0.8f,
            bgR = 0.1f, bgG = 0.1f, bgB = 0.1f, bgA = 0.8f
        )
    }

    private fun renderEngagementRing(
        centerX: Float,
        centerY: Float,
        playerX: Float,
        playerY: Float,
        zoom: Float,
        palette: Int
    ) {
        val radiusTiles = 3.5f
        val segments = 24
        val step = (2 * Math.PI) / segments

        for (i in 0 until segments) {
            val angle = i * step
            val gX = playerX + (cos(angle) * radiusTiles).toFloat()
            val gY = playerY + (sin(angle) * radiusTiles).toFloat()
            val (isoX, isoY) = characterBuffer.gridToIso(gX, gY, 0.01f, centerX, centerY, playerX, playerY, zoom)

            characterBuffer.pushCharCell(
                cx = isoX, cy = isoY,
                halfW = 3.5f * zoom, halfH = 3.5f * zoom, z = 0.01f,
                char = '.',
                fgR = 0.31f, fgG = 0.82f, fgB = 0.77f, fgA = 0.40f
            )
        }
    }

    private fun getPaletteClearColor(paletteIndex: Int): Triple<Float, Float, Float> {
        return when (paletteIndex) {
            1 -> Triple(0.04f, 0.05f, 0.08f) // ANSI 256
            2 -> Triple(0.0f, 0.0f, 0.0f)     // ANSI 16
            3 -> Triple(0.01f, 0.04f, 0.02f) // Phosphor Green
            4 -> Triple(0.05f, 0.03f, 0.01f) // Amber
            5 -> Triple(0.01f, 0.03f, 0.06f) // Cyan
            6 -> Triple(0.0f, 0.03f, 0.01f)  // Matrix
            else -> Triple(0.03f, 0.04f, 0.05f) // HDR Dark Wasteland
        }
    }
}
