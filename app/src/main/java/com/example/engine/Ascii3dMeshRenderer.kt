package com.example.engine

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * OpenGL ES 2.0 3D Mesh to 2D ASCII Luminance Renderer.
 *
 * Renders 3D geometry (cubes, spheres, props, entity models) and maps surface
 * luminance dynamically to 2D ASCII character glyphs using custom shaders and a font atlas texture.
 */
class Ascii3dMeshRenderer {

    private var programId = 0
    private var uMVPMatrixLoc = -1
    private var uModelMatrixLoc = -1
    private var uNormalMatrixLoc = -1
    private var uLightPosLoc = -1
    private var uCameraPosLoc = -1
    private var uTimeLoc = -1
    private var uFontAtlasLoc = -1
    private var uMeshTextureLoc = -1
    private var uUseMeshTextureLoc = -1
    private var uScreenResolutionLoc = -1
    private var uCellSizeLoc = -1
    private var uLuminanceScaleLoc = -1
    private var uScanlineIntensityLoc = -1
    private var uTerminalColorLoc = -1

    private var aPositionLoc = -1
    private var aNormalLoc = -1
    private var aTexCoordLoc = -1
    private var aColorLoc = -1

    // Pre-allocated matrices for 3D transformations
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    // Pre-built 3D primitive mesh (Isometric Crate / Vault Mesh)
    private var cubeVertexBuffer: FloatBuffer? = null
    private var cubeIndexBuffer: ShortBuffer? = null
    private var cubeIndexCount = 0

    fun init() {
        programId = AsciiShaders.createMesh3dProgram()
        if (programId == 0) return

        uMVPMatrixLoc = GLES20.glGetUniformLocation(programId, "u_MVPMatrix")
        uModelMatrixLoc = GLES20.glGetUniformLocation(programId, "u_ModelMatrix")
        uNormalMatrixLoc = GLES20.glGetUniformLocation(programId, "u_NormalMatrix")
        uLightPosLoc = GLES20.glGetUniformLocation(programId, "u_LightPos")
        uCameraPosLoc = GLES20.glGetUniformLocation(programId, "u_CameraPos")
        uTimeLoc = GLES20.glGetUniformLocation(programId, "u_Time")
        uFontAtlasLoc = GLES20.glGetUniformLocation(programId, "u_FontAtlas")
        uMeshTextureLoc = GLES20.glGetUniformLocation(programId, "u_MeshTexture")
        uUseMeshTextureLoc = GLES20.glGetUniformLocation(programId, "u_UseMeshTexture")
        uScreenResolutionLoc = GLES20.glGetUniformLocation(programId, "u_ScreenResolution")
        uCellSizeLoc = GLES20.glGetUniformLocation(programId, "u_CellSize")
        uLuminanceScaleLoc = GLES20.glGetUniformLocation(programId, "u_LuminanceScale")
        uScanlineIntensityLoc = GLES20.glGetUniformLocation(programId, "u_ScanlineIntensity")
        uTerminalColorLoc = GLES20.glGetUniformLocation(programId, "u_TerminalColor")

        aPositionLoc = GLES20.glGetAttribLocation(programId, "a_Position")
        aNormalLoc = GLES20.glGetAttribLocation(programId, "a_Normal")
        aTexCoordLoc = GLES20.glGetAttribLocation(programId, "a_TexCoord")
        aColorLoc = GLES20.glGetAttribLocation(programId, "a_Color")

        initCubeMesh()
    }

    private fun initCubeMesh() {
        // 24 vertices for a 3D cube with distinct face normals
        // Format per vertex: posX, posY, posZ, normX, normY, normZ, u, v, r, g, b, a (12 floats)
        val vertices = floatArrayOf(
            // Front face (Z = +1, normal = 0, 0, 1)
            -1f, -1f,  1f,   0f,  0f,  1f,   0f, 1f,   0.2f, 0.9f, 0.7f, 1.0f,
             1f, -1f,  1f,   0f,  0f,  1f,   1f, 1f,   0.2f, 0.9f, 0.7f, 1.0f,
             1f,  1f,  1f,   0f,  0f,  1f,   1f, 0f,   0.2f, 0.9f, 0.7f, 1.0f,
            -1f,  1f,  1f,   0f,  0f,  1f,   0f, 0f,   0.2f, 0.9f, 0.7f, 1.0f,

            // Back face (Z = -1, normal = 0, 0, -1)
            -1f, -1f, -1f,   0f,  0f, -1f,   1f, 1f,   0.1f, 0.6f, 0.5f, 1.0f,
            -1f,  1f, -1f,   0f,  0f, -1f,   1f, 0f,   0.1f, 0.6f, 0.5f, 1.0f,
             1f,  1f, -1f,   0f,  0f, -1f,   0f, 0f,   0.1f, 0.6f, 0.5f, 1.0f,
             1f, -1f, -1f,   0f,  0f, -1f,   0f, 1f,   0.1f, 0.6f, 0.5f, 1.0f,

            // Top face (Y = +1, normal = 0, 1, 0)
            -1f,  1f, -1f,   0f,  1f,  0f,   0f, 1f,   0.3f, 1.0f, 0.8f, 1.0f,
            -1f,  1f,  1f,   0f,  1f,  0f,   0f, 0f,   0.3f, 1.0f, 0.8f, 1.0f,
             1f,  1f,  1f,   0f,  1f,  0f,   1f, 0f,   0.3f, 1.0f, 0.8f, 1.0f,
             1f,  1f, -1f,   0f,  1f,  0f,   1f, 1f,   0.3f, 1.0f, 0.8f, 1.0f,

            // Bottom face (Y = -1, normal = 0, -1, 0)
            -1f, -1f, -1f,   0f, -1f,  0f,   0f, 0f,   0.1f, 0.4f, 0.3f, 1.0f,
             1f, -1f, -1f,   0f, -1f,  0f,   1f, 0f,   0.1f, 0.4f, 0.3f, 1.0f,
             1f, -1f,  1f,   0f, -1f,  0f,   1f, 1f,   0.1f, 0.4f, 0.3f, 1.0f,
            -1f, -1f,  1f,   0f, -1f,  0f,   0f, 1f,   0.1f, 0.4f, 0.3f, 1.0f,

            // Right face (X = +1, normal = 1, 0, 0)
             1f, -1f, -1f,   1f,  0f,  0f,   1f, 1f,   0.15f, 0.7f, 0.6f, 1.0f,
             1f,  1f, -1f,   1f,  0f,  0f,   1f, 0f,   0.15f, 0.7f, 0.6f, 1.0f,
             1f,  1f,  1f,   1f,  0f,  0f,   0f, 0f,   0.15f, 0.7f, 0.6f, 1.0f,
             1f, -1f,  1f,   1f,  0f,  0f,   0f, 1f,   0.15f, 0.7f, 0.6f, 1.0f,

            // Left face (X = -1, normal = -1, 0, 0)
            -1f, -1f, -1f,  -1f,  0f,  0f,   0f, 1f,   0.15f, 0.7f, 0.6f, 1.0f,
            -1f, -1f,  1f,  -1f,  0f,  0f,   1f, 1f,   0.15f, 0.7f, 0.6f, 1.0f,
            -1f,  1f,  1f,  -1f,  0f,  0f,   1f, 0f,   0.15f, 0.7f, 0.6f, 1.0f,
            -1f,  1f, -1f,  -1f,  0f,  0f,   0f, 0f,   0.15f, 0.7f, 0.6f, 1.0f
        )

        val indices = shortArrayOf(
            0,  1,  2,      0,  2,  3,    // front
            4,  5,  6,      4,  6,  7,    // back
            8,  9,  10,     8,  10, 11,   // top
            12, 13, 14,     12, 14, 15,   // bottom
            16, 17, 18,     16, 18, 19,   // right
            20, 21, 22,     20, 22, 23    // left
        )

        cubeIndexCount = indices.size

        cubeVertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertices)
                position(0)
            }

        cubeIndexBuffer = ByteBuffer.allocateDirect(indices.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indices)
                position(0)
            }
    }

    /**
     * Renders a 3D Mesh transformed as 2D ASCII character glyphs using continuous luminance mapping.
     */
    fun render3dMesh(
        fontAtlasTextureId: Int,
        screenWidth: Int,
        screenHeight: Int,
        timeSec: Float,
        posX: Float,
        posY: Float,
        posZ: Float,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
        scale: Float,
        lightX: Float = 3f,
        lightY: Float = 5f,
        lightZ: Float = 4f,
        scanlineIntensity: Float = 0.5f,
        terminalColorR: Float = 0.2f,
        terminalColorG: Float = 0.95f,
        terminalColorB: Float = 0.75f
    ) {
        if (programId == 0 || cubeVertexBuffer == null || cubeIndexBuffer == null) return

        GLES20.glUseProgram(programId)

        val aspect = screenWidth.toFloat() / screenHeight.toFloat().coerceAtLeast(1f)
        Matrix.perspectiveM(projectionMatrix, 0, 45f, aspect, 0.1f, 100f)

        val cameraX = 0f
        val cameraY = 2.5f
        val cameraZ = 5.5f
        Matrix.setLookAtM(viewMatrix, 0, cameraX, cameraY, cameraZ, 0f, 0f, 0f, 0f, 1f, 0f)

        // Setup Model Matrix
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, posX, posY, posZ)
        Matrix.rotateM(modelMatrix, 0, rotX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotY, 0f, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotZ, 0f, 0f, 1f)
        Matrix.scaleM(modelMatrix, 0, scale, scale, scale)

        // MVP Matrix = Projection * View * Model
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        // Normal Matrix (inverse transpose of Model Matrix)
        Matrix.invertM(tempMatrix, 0, modelMatrix, 0)
        Matrix.transposeM(normalMatrix, 0, tempMatrix, 0)

        // Upload Uniforms
        GLES20.glUniformMatrix4fv(uMVPMatrixLoc, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES20.glUniformMatrix4fv(uNormalMatrixLoc, 1, false, normalMatrix, 0)

        GLES20.glUniform3f(uLightPosLoc, lightX, lightY, lightZ)
        GLES20.glUniform3f(uCameraPosLoc, cameraX, cameraY, cameraZ)
        GLES20.glUniform1f(uTimeLoc, timeSec)
        GLES20.glUniform2f(uScreenResolutionLoc, screenWidth.toFloat(), screenHeight.toFloat())
        GLES20.glUniform2f(uCellSizeLoc, 12f, 12f)
        GLES20.glUniform1f(uLuminanceScaleLoc, 1.15f)
        GLES20.glUniform1f(uScanlineIntensityLoc, scanlineIntensity)
        GLES20.glUniform3f(uTerminalColorLoc, terminalColorR, terminalColorG, terminalColorB)
        GLES20.glUniform1i(uUseMeshTextureLoc, 0)

        // Bind Font Atlas Texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fontAtlasTextureId)
        GLES20.glUniform1i(uFontAtlasLoc, 0)

        val stride = 12 * 4 // 12 floats per vertex
        val buffer = cubeVertexBuffer ?: return

        // Position: offset 0, 3 floats
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        buffer.position(0)
        GLES20.glVertexAttribPointer(aPositionLoc, 3, GLES20.GL_FLOAT, false, stride, buffer)

        // Normal: offset 3, 3 floats
        GLES20.glEnableVertexAttribArray(aNormalLoc)
        buffer.position(3)
        GLES20.glVertexAttribPointer(aNormalLoc, 3, GLES20.GL_FLOAT, false, stride, buffer)

        // TexCoord: offset 6, 2 floats
        GLES20.glEnableVertexAttribArray(aTexCoordLoc)
        buffer.position(6)
        GLES20.glVertexAttribPointer(aTexCoordLoc, 2, GLES20.GL_FLOAT, false, stride, buffer)

        // Color: offset 8, 4 floats
        GLES20.glEnableVertexAttribArray(aColorLoc)
        buffer.position(8)
        GLES20.glVertexAttribPointer(aColorLoc, 4, GLES20.GL_FLOAT, false, stride, buffer)

        // Draw indexed 3D mesh
        cubeIndexBuffer?.position(0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, cubeIndexCount, GLES20.GL_UNSIGNED_SHORT, cubeIndexBuffer)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aNormalLoc)
        GLES20.glDisableVertexAttribArray(aTexCoordLoc)
        GLES20.glDisableVertexAttribArray(aColorLoc)
    }

    fun release() {
        if (programId != 0) {
            GLES20.glDeleteProgram(programId)
            programId = 0
        }
        cubeVertexBuffer = null
        cubeIndexBuffer = null
    }
}
