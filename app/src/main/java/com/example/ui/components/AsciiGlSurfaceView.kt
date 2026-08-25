package com.example.ui.components

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import com.example.data.Enemy
import com.example.data.FloatingText
import com.example.data.Player
import com.example.data.TileType
import com.example.engine.AsciiCharacterBuffer
import com.example.engine.AsciiGlRenderer
import com.example.engine.LightSource

/**
 * Custom GLSurfaceView for OpenGL ES 2.0 Hardware-Accelerated 2.5D Isometric ASCII Rendering.
 *
 * Features:
 * - Multi-touch Pinch-to-Zoom gesture detector with focal-point preservation for inspecting fine ASCII glyphs on mobile screens
 * - Dual-finger panning during pinch scaling
 * - Double-tap quick-inspection macro zoom toggle
 * - 2.5D Isometric raycasting for screen-to-grid coordinate conversion
 * - Continuous high-framerate rendering with OpenGL ES 2.0 context preservation
 */
class AsciiGlSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    companion object {
        const val MIN_ZOOM = 0.45f
        const val MAX_ZOOM = 3.80f
        const val DEFAULT_ZOOM = 1.05f
        const val INSPECTION_ZOOM = 2.40f
    }

    val renderer = AsciiGlRenderer()

    var onTileTapped: ((Int, Int) -> Unit)? = null
    var onLongPressed: ((Float, Float, Int, Int) -> Unit)? = null
    var onZoomChanged: ((Float) -> Unit)? = null

    var panOffsetX = 0f
    var panOffsetY = 0f
    var zoomLevel = DEFAULT_ZOOM

    private var playerX = 5.0f
    private var playerY = 5.0f

    private var lastFocusX = 0f
    private var lastFocusY = 0f

    val scaleDetector: ScaleGestureDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                lastFocusX = detector.focusX
                lastFocusY = detector.focusY
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                if (scaleFactor.isNaN() || scaleFactor.isInfinite() || scaleFactor == 1.0f) {
                    return false
                }

                val oldZoom = zoomLevel
                val newZoom = (oldZoom * scaleFactor).coerceIn(MIN_ZOOM, MAX_ZOOM)
                if (newZoom == oldZoom) return false

                val focusX = detector.focusX
                val focusY = detector.focusY

                // Calculate current screen center
                val viewportCenterX = width * 0.5f + panOffsetX
                val viewportCenterY = height * 0.44f + panOffsetY

                // Adjust pan offsets to keep the world coordinate beneath the pinch focal center stationary
                val zoomRatio = newZoom / oldZoom
                panOffsetX = (focusX - (width * 0.5f)) - (focusX - viewportCenterX) * zoomRatio
                panOffsetY = (focusY - (height * 0.44f)) - (focusY - viewportCenterY) * zoomRatio

                // Apply two-finger focal pan translation
                val focusDeltaX = focusX - lastFocusX
                val focusDeltaY = focusY - lastFocusY
                panOffsetX += focusDeltaX
                panOffsetY += focusDeltaY
                lastFocusX = focusX
                lastFocusY = focusY

                zoomLevel = newZoom
                onZoomChanged?.invoke(zoomLevel)
                requestRender()
                return true
            }
        }
    )

    val gestureDetector: GestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleDetector.isInProgress) return false
                panOffsetX -= distanceX
                panOffsetY -= distanceY
                requestRender()
                return true
            }

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val centerX = width * 0.5f + panOffsetX
                val centerY = height * 0.44f + panOffsetY

                val gridCoords = screenToGrid(
                    screenX = e.x,
                    screenY = e.y,
                    centerX = centerX,
                    centerY = centerY,
                    camX = playerX,
                    camY = playerY,
                    zoom = zoomLevel
                )
                onTileTapped?.invoke(gridCoords.first, gridCoords.second)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val targetZoom = if (zoomLevel >= 1.8f) DEFAULT_ZOOM else INSPECTION_ZOOM
                zoomTo(targetZoom, e.x, e.y)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (scaleDetector.isInProgress) return
                val centerX = width * 0.5f + panOffsetX
                val centerY = height * 0.44f + panOffsetY

                val gridCoords = screenToGrid(
                    screenX = e.x,
                    screenY = e.y,
                    centerX = centerX,
                    centerY = centerY,
                    camX = playerX,
                    camY = playerY,
                    zoom = zoomLevel
                )
                onLongPressed?.invoke(e.x, e.y, gridCoords.first, gridCoords.second)
            }
        }
    )

    init {
        // Configure OpenGL ES 2.0 Context
        setEGLContextClientVersion(2)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        holder.setFormat(android.graphics.PixelFormat.RGBA_8888)
        preserveEGLContextOnPause = true
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        var handled = scaleDetector.onTouchEvent(event)
        if (!scaleDetector.isInProgress) {
            handled = gestureDetector.onTouchEvent(event) || handled
        }
        return handled || super.onTouchEvent(event)
    }

    /**
     * Smoothly sets the camera zoom level relative to a focal point.
     */
    fun zoomTo(
        targetZoom: Float,
        focusX: Float = width * 0.5f,
        focusY: Float = height * 0.44f
    ) {
        val oldZoom = zoomLevel
        val newZoom = targetZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (newZoom == oldZoom) return

        val viewportCenterX = width * 0.5f + panOffsetX
        val viewportCenterY = height * 0.44f + panOffsetY

        val zoomRatio = newZoom / oldZoom
        panOffsetX = (focusX - (width * 0.5f)) - (focusX - viewportCenterX) * zoomRatio
        panOffsetY = (focusY - (height * 0.44f)) - (focusY - viewportCenterY) * zoomRatio

        zoomLevel = newZoom
        onZoomChanged?.invoke(zoomLevel)
        requestRender()
    }

    fun zoomIn(step: Float = 0.25f) {
        zoomTo(zoomLevel + step)
    }

    fun zoomOut(step: Float = 0.25f) {
        zoomTo(zoomLevel - step)
    }

    /**
     * Converts screen-space touch coordinates (screenX, screenY) back to 2.5D Isometric grid coordinates.
     */
    fun screenToGrid(
        screenX: Float,
        screenY: Float,
        centerX: Float,
        centerY: Float,
        camX: Float,
        camY: Float,
        zoom: Float
    ): Pair<Int, Int> {
        val relScreenX = (screenX - centerX) / (zoom * (AsciiCharacterBuffer.TILE_WIDTH * 0.5f))
        val relScreenY = (screenY - centerY) / (zoom * (AsciiCharacterBuffer.TILE_HEIGHT * 0.5f))

        val gridX = (relScreenY + relScreenX) * 0.5f + camX
        val gridY = (relScreenY - relScreenX) * 0.5f + camY

        val cellX = if (gridX >= 0) gridX.toInt() else gridX.toInt() - 1
        val cellY = if (gridY >= 0) gridY.toInt() else gridY.toInt() - 1

        return Pair(cellX, cellY)
    }

    fun updateRenderState(
        mapGrid: List<List<TileType>>,
        player: Player,
        enemies: List<Enemy>,
        lightSources: List<LightSource>,
        selectedTile: Pair<Int, Int>?,
        discoveredTiles: Set<Pair<Int, Int>>,
        floatingTexts: List<FloatingText>,
        paletteIndex: Int,
        scanlineIntensity: Float = 0.55f,
        ditherStrength: Float = 1.0f,
        screenShakeIntensity: Float = 0f,
        screenShakeStartTime: Long = 0L
    ) {
        playerX = player.x
        playerY = player.y

        val snapshot = AsciiGlRenderer.RenderSnapshot(
            mapGrid = mapGrid,
            player = player,
            enemies = enemies,
            lightSources = lightSources,
            selectedTile = selectedTile,
            discoveredTiles = discoveredTiles,
            floatingTexts = floatingTexts,
            paletteIndex = paletteIndex,
            panOffsetX = panOffsetX,
            panOffsetY = panOffsetY,
            shakeIntensity = screenShakeIntensity,
            shakeStartTime = screenShakeStartTime,
            zoomLevel = zoomLevel,
            scanlineIntensity = scanlineIntensity,
            ditherStrength = ditherStrength
        )
        renderer.updateState(snapshot)
    }

    fun recenterCamera() {
        panOffsetX = 0f
        panOffsetY = 0f
        zoomLevel = DEFAULT_ZOOM
        onZoomChanged?.invoke(zoomLevel)
        requestRender()
    }
}

