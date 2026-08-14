package com.example.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.opengl.GLES20
import android.opengl.GLUtils

object FontAtlasGenerator {

    /**
     * Creates a 256x256 font atlas texture containing 256 ASCII character glyphs (16x16 grid of 16x16 px cells).
     * Returns the OpenGL ES 2.0 Texture Handle.
     */
    fun createFontAtlasTexture(): Int {
        val atlasSize = 256
        val cellSize = 16
        val bitmap = Bitmap.createBitmap(atlasSize, atlasSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.TRANSPARENT)

        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 13f
            typeface = Typeface.MONOSPACE
            isAntiAlias = false
            textAlign = Paint.Align.CENTER
        }

        // Font characters map (16x16 grid = 256 cells)
        val asciiChars = listOf(
            ' ', '.', ':', '-', '=', '+', '*', 'o', '%', 'a', '#', '@', 'M', 'W', '$', '&',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
            'G', 'H', 'I', 'J', 'K', 'L', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'X',
            'Y', 'Z', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', 'p',
            'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '!', '?', '/', '\\', '|', '<',
            '>', '(', ')', '[', ']', '{', '}', '^', '_', '~', '`', '\'', '"', ';', ',', '+'
        )

        val fontMetrics = paint.fontMetrics
        val yOffset = (cellSize - (fontMetrics.descent + fontMetrics.ascent)) / 2f

        for (i in 0 until 256) {
            val charToDraw = if (i < asciiChars.size) asciiChars[i] else (i % 95 + 32).toChar()
            val col = i % 16
            val row = i / 16
            val x = col * cellSize + cellSize / 2f
            val y = row * cellSize + yOffset
            canvas.drawText(charToDraw.toString(), x, y, paint)
        }

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        val textureId = textures[0]

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        bitmap.recycle()

        return textureId
    }
}
