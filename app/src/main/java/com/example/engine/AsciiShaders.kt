package com.example.engine

import android.opengl.GLES20
import android.util.Log

/**
 * Custom OpenGL ES 2.0 Vertex and Fragment Shaders for the 2.5D Isometric ASCII Renderer.
 *
 * Capabilities:
 * - 2.5D Isometric perspective vertex projection with elevation offset and dynamic wave oscillation
 * - Font Atlas glyph alpha extraction and foreground/background color composition
 * - Per-character dynamic photon lighting falloff and Lambertian normal shading
 * - Hardware Bayer 4x4 spatial ordered dithering to eliminate color banding
 * - CRT Phosphor scanline emulation, scan-beam sweep, and chromatic vignette
 */
object AsciiShaders {

    private const val TAG = "AsciiShaders"

    val VERTEX_SHADER = """
        precision mediump float;

        uniform mat4 u_MVPMatrix;
        uniform float u_Time;
        uniform vec2 u_MapDimensions;

        attribute vec3 a_Position;    // x, y (screen or isometric pos), z (elevation / layer)
        attribute vec2 a_TexCoord;    // Font atlas UV coordinate
        attribute vec4 a_FgColor;     // Character foreground color (RGBA)
        attribute vec4 a_BgColor;     // Tile background color (RGBA)
        attribute vec4 a_LightParams; // x: intensity, y: dither flag, z: glow/pulse flag, w: wave anim flag
        attribute vec3 a_LightColor;  // Dynamic photon light tint (RGB)
        attribute vec4 a_EdgeParams;  // x: edgeFlag, y: edgeStrength, z: gridX, w: gridY

        varying vec2 v_TexCoord;
        varying vec4 v_FgColor;
        varying vec4 v_BgColor;
        varying vec4 v_LightParams;
        varying vec3 v_LightColor;
        varying vec4 v_EdgeParams;
        varying vec2 v_ScreenPos;
        varying vec2 v_GridPos;

        void main() {
            vec4 pos = vec4(a_Position, 1.0);

            // Dynamic fluid / toxic pool vertex wave oscillation if flag set in a_LightParams.w
            if (a_LightParams.w > 0.5) {
                float wave = sin(u_Time * 3.8 + a_Position.x * 0.04 + a_Position.y * 0.04) * 2.2;
                pos.y += wave;
            }

            vec4 transformedPos = u_MVPMatrix * pos;
            gl_Position = transformedPos;

            v_TexCoord = a_TexCoord;
            v_FgColor = a_FgColor;
            v_BgColor = a_BgColor;
            v_LightParams = a_LightParams;
            v_LightColor = a_LightColor;
            v_EdgeParams = a_EdgeParams;
            v_ScreenPos = (transformedPos.xy / max(transformedPos.w, 0.0001)) * 0.5 + 0.5;
            v_GridPos = vec2(a_EdgeParams.z, a_EdgeParams.w);
        }
    """.trimIndent()

    val FRAGMENT_SHADER = """
        precision mediump float;

        uniform sampler2D u_FontAtlas;
        uniform sampler2D u_LightMap;
        uniform int u_UseLightMap;
        uniform float u_Time;
        uniform float u_ScanlineIntensity;
        uniform float u_DitherStrength;
        uniform vec2 u_Resolution;
        uniform vec2 u_MapDimensions;

        // Custom Sharpness & Ambient Environment Controls
        uniform float u_Sharpness;
        uniform float u_AmbientDarkness;

        // Custom Ramp & Edge Isolation Uniforms
        uniform float u_EdgeIsolationStrength;
        uniform float u_RampQuantization;
        uniform vec3 u_RampHighlightTint;
        uniform vec3 u_RampShadowTint;
        uniform vec3 u_EdgeTint;

        // Flashlight Lighting Uniforms
        uniform int u_FlashlightEnabled;
        uniform vec3 u_FlashlightPos;    // x: gridX, y: gridY, z: facingAngleRad
        uniform vec4 u_FlashlightParams; // x: range, y: innerConeCos, z: outerConeCos, w: intensity
        uniform vec4 u_FlashlightColor;  // x, y, z: photon RGB, w: flicker delta

        // Environmental Point Lights Uniforms (up to 6 lights)
        uniform int u_EnvLightCount;
        uniform vec4 u_EnvLightPos[6];   // x, y (gridX, gridY), z (radius), w (intensity)
        uniform vec4 u_EnvLightColor[6]; // x, y, z (RGB 0..1), w (pulseSpeed)

        varying vec2 v_TexCoord;
        varying vec4 v_FgColor;
        varying vec4 v_BgColor;
        varying vec4 v_LightParams;
        varying vec3 v_LightColor;
        varying vec4 v_EdgeParams;
        varying vec2 v_ScreenPos;
        varying vec2 v_GridPos;

        // 4x4 Bayer Matrix for high-performance spatial dither
        float getBayer4x4(vec2 screenPos) {
            int x = int(mod(screenPos.x, 4.0));
            int y = int(mod(screenPos.y, 4.0));
            int idx = y * 4 + x;

            if (idx == 0) return -0.500;
            if (idx == 1) return 0.000;
            if (idx == 2) return -0.375;
            if (idx == 3) return 0.125;
            if (idx == 4) return 0.250;
            if (idx == 5) return -0.250;
            if (idx == 6) return 0.375;
            if (idx == 7) return -0.125;
            if (idx == 8) return -0.3125;
            if (idx == 9) return 0.1875;
            if (idx == 10) return -0.4375;
            if (idx == 11) return 0.0625;
            if (idx == 12) return 0.4375;
            if (idx == 13) return -0.0625;
            if (idx == 14) return 0.3125;
            return -0.1875;
        }

        void main() {
            // Sample glyph mask from font atlas texture
            vec4 atlasSample = texture2D(u_FontAtlas, v_TexCoord);
            float rawGlyphMask = max(atlasSample.a, atlasSample.r);

            // 1. High-Precision Adaptive Glyph Sharpness
            float sharpnessFactor = max(u_Sharpness, 1.0);
            float halfWidth = clamp(0.24 / sharpnessFactor, 0.01, 0.35);
            float crispGlyph = smoothstep(0.48 - halfWidth, 0.48 + halfWidth, rawGlyphMask);
            float glyphMask = mix(rawGlyphMask, crispGlyph, clamp(sharpnessFactor * 0.45, 0.0, 1.0));

            // Compute lighting base from vertex
            float lightIntensity = v_LightParams.x;
            vec3 lightRgb = v_LightColor;

            // 2. Texture-based Light-Map Sampling (with grid UV or screen fallback)
            if (u_UseLightMap == 1) {
                vec2 lmUv = (u_MapDimensions.x > 0.0 && (v_GridPos.x > 0.0 || v_GridPos.y > 0.0))
                    ? (v_GridPos / max(u_MapDimensions, vec2(1.0)))
                    : v_ScreenPos;
                vec4 lmSample = texture2D(u_LightMap, lmUv);
                vec3 lmColor = lmSample.rgb;
                float lmInt = lmSample.a * 2.0;
                lightRgb = mix(lightRgb, max(lightRgb, lmColor), 0.75);
                lightIntensity = max(lightIntensity, lmInt);
            }

            // 3. Direct GPU Distance-Based Flashlight Calculation
            float distToPlayer = length(v_GridPos - u_FlashlightPos.xy);
            if (u_FlashlightEnabled == 1) {
                vec2 toFrag = v_GridPos - u_FlashlightPos.xy;
                float dist = distToPlayer;
                if (dist > 0.05 && dist <= u_FlashlightParams.x) {
                    vec2 dirNorm = toFrag / dist;
                    vec2 flashDir = vec2(cos(u_FlashlightPos.z), sin(u_FlashlightPos.z));
                    float dotVal = dot(dirNorm, flashDir);
                    if (dotVal > u_FlashlightParams.z) {
                        float spotFactor = clamp((dotVal - u_FlashlightParams.z) / max(u_FlashlightParams.y - u_FlashlightParams.z, 0.0001), 0.0, 1.0);
                        // Distance-based polynomial quadratic attenuation
                        float distAtten = 1.0 / (1.0 + 0.10 * dist + 0.045 * dist * dist);
                        float flashPower = spotFactor * distAtten * (u_FlashlightParams.w + u_FlashlightColor.a);
                        lightIntensity += flashPower;
                        lightRgb = mix(lightRgb, lightRgb + u_FlashlightColor.rgb * 1.5, clamp(flashPower * 0.8, 0.0, 1.0));
                    }

                    // Soft 360-degree bio-suit proximity aura
                    float auraRadius = 2.8;
                    if (dist <= auraRadius) {
                        float auraAtten = clamp(1.0 - (dist / auraRadius), 0.0, 1.0);
                        float auraPower = auraAtten * 0.45;
                        lightIntensity += auraPower;
                        lightRgb = mix(lightRgb, vec3(0.40, 0.80, 0.95), auraPower * 0.5);
                    }
                }
            }

            // 4. Direct GPU Distance-Based Environmental Light Sources
            for (int i = 0; i < 6; i++) {
                if (i >= u_EnvLightCount) break;
                vec2 toLight = v_GridPos - u_EnvLightPos[i].xy;
                float lDist = length(toLight);
                float lRadius = u_EnvLightPos[i].z;
                if (lDist <= lRadius) {
                    float lAtten = clamp(1.0 - (lDist / lRadius), 0.0, 1.0);
                    float distFactor = lAtten * lAtten; // Quadratic attenuation for soft gradient
                    float pulse = 1.0;
                    if (u_EnvLightColor[i].w > 0.0) {
                        pulse = sin(u_Time * u_EnvLightColor[i].w) * 0.15 + 0.85;
                    }
                    float envPower = distFactor * u_EnvLightPos[i].w * pulse;
                    lightIntensity += envPower;
                    lightRgb = mix(lightRgb, lightRgb + u_EnvLightColor[i].rgb * 1.4, clamp(envPower * 0.7, 0.0, 1.0));
                }
            }

            // 5. Adaptive Coordinate Darkness & Subterranean Shadow Falloff
            // As coordinates move further from player and into dungeon depths, decay ambient floor light
            float spatialDarkness = clamp((distToPlayer - 3.2) / 8.5, 0.0, 1.0);
            float ambientFloor = mix(0.18, 0.02, spatialDarkness * clamp(u_AmbientDarkness, 0.0, 1.0));
            lightIntensity = max(lightIntensity, ambientFloor);

            // Spatial Bayer Dithering
            if (v_LightParams.y > 0.5 && u_DitherStrength > 0.0) {
                float ditherVal = getBayer4x4(gl_FragCoord.xy) * (u_DitherStrength * 0.12);
                lightIntensity = clamp(lightIntensity + ditherVal, 0.0, 2.5);
            }

            // Glow / Pulse Modulation
            if (v_LightParams.z > 0.5) {
                float pulse = sin(u_Time * 5.0) * 0.20 + 0.20;
                lightIntensity += pulse;
            }

            // Custom Ramp Quantization & Color Tonal Mapping
            if (u_RampQuantization > 0.0) {
                float normalizedLight = clamp(lightIntensity / 1.5, 0.0, 1.0);
                float quantized = floor(normalizedLight * u_RampQuantization + 0.5) / u_RampQuantization;
                vec3 rampTint = mix(u_RampShadowTint, u_RampHighlightTint, quantized);
                lightRgb = mix(lightRgb, lightRgb * rampTint * 1.4, 0.55);
                lightIntensity = quantized * 1.5;
            }

            // Subterranean Shadow Tint (deep atmospheric indigo-obsidian for pitch dark zones)
            vec3 ambientShadowTint = vec3(0.012, 0.016, 0.026);
            float shadowCoeff = clamp(lightIntensity / 0.35, 0.0, 1.0);

            // Apply dynamic lighting and photon color to foreground & background
            vec3 litFg = mix(ambientShadowTint * 1.8, v_FgColor.rgb * lightRgb * lightIntensity, shadowCoeff * 0.85 + 0.15);
            vec3 litBg = mix(ambientShadowTint, v_BgColor.rgb * lightRgb * (lightIntensity * 0.85), shadowCoeff);

            // Composite character glyph over background tile
            vec3 finalRgb = mix(litBg, litFg, glyphMask);
            float finalAlpha = max(v_BgColor.a, v_FgColor.a * glyphMask);

            if (finalAlpha <= 0.01) {
                discard;
            }

            // Sharp Edge Isolation & Enhanced Contour Contrast
            if (v_EdgeParams.x > 0.5 && u_EdgeIsolationStrength > 0.0) {
                float edgeBoost = v_EdgeParams.y * u_EdgeIsolationStrength * max(u_Sharpness * 0.8, 1.0);
                vec3 edgeColor = mix(finalRgb * 1.85, u_EdgeTint, 0.70);
                finalRgb = mix(finalRgb, edgeColor, clamp(edgeBoost, 0.0, 1.0));
            }

            // CRT Scanline Emulation
            if (u_ScanlineIntensity > 0.0) {
                float scanline = sin(gl_FragCoord.y * 1.5707963) * 0.5 + 0.5;
                float scanDim = 1.0 - (u_ScanlineIntensity * 0.25 * (1.0 - scanline));
                finalRgb *= scanDim;
            }

            // CRT Cathode Vignette
            vec2 centerOffset = v_ScreenPos * 2.0 - 1.0;
            float vignette = clamp(1.0 - dot(centerOffset, centerOffset) * 0.08, 0.0, 1.0);
            finalRgb *= vignette;

            gl_FragColor = vec4(finalRgb, finalAlpha);
        }
    """.trimIndent()

    /**
     * OpenGL ES 2.0 Vertex Shader for 3D Meshes to 2D ASCII Glyph Transformation.
     *
     * Takes 3D model vertices, normals, UVs, and colors, computing 3D lighting vectors,
     * surface normals, and screen-space coordinates for ASCII luminance sampling.
     */
    val MESH_3D_ASCII_VERTEX_SHADER = """
        precision mediump float;

        uniform mat4 u_MVPMatrix;
        uniform mat4 u_ModelMatrix;
        uniform mat4 u_NormalMatrix;
        uniform vec3 u_LightPos;
        uniform vec3 u_CameraPos;
        uniform float u_Time;

        attribute vec3 a_Position;    // 3D model vertex position (x, y, z)
        attribute vec3 a_Normal;      // 3D surface normal vector (nx, ny, nz)
        attribute vec2 a_TexCoord;    // Mesh diffuse/albedo texture coordinates (u, v)
        attribute vec4 a_Color;       // Base mesh vertex color (RGBA)

        varying vec3 v_WorldPos;
        varying vec3 v_Normal;
        varying vec2 v_TexCoord;
        varying vec4 v_Color;
        varying vec3 v_LightDir;
        varying vec3 v_ViewDir;
        varying vec2 v_ScreenCoord;

        void main() {
            // Transform vertex into 3D world space
            vec4 worldPos = u_ModelMatrix * vec4(a_Position, 1.0);
            v_WorldPos = worldPos.xyz;

            // Transform normal with normal matrix to preserve perpendicularity
            v_Normal = normalize((u_NormalMatrix * vec4(a_Normal, 0.0)).xyz);

            // Compute lighting and view vectors in 3D world space
            v_LightDir = normalize(u_LightPos - worldPos.xyz);
            v_ViewDir = normalize(u_CameraPos - worldPos.xyz);

            v_TexCoord = a_TexCoord;
            v_Color = a_Color;

            // Compute final clip-space position
            vec4 clipPos = u_MVPMatrix * vec4(a_Position, 1.0);
            gl_Position = clipPos;

            // Normalized screen-space coordinate for 2D ASCII grid quantization
            v_ScreenCoord = (clipPos.xy / max(clipPos.w, 0.0001)) * 0.5 + 0.5;
        }
    """.trimIndent()

    /**
     * OpenGL ES 2.0 Fragment Shader that maps 3D Mesh surface luminance to 2D ASCII character glyphs.
     *
     * Algorithm:
     * 1. Evaluates 3D Blinn-Phong lighting (ambient + diffuse + specular).
     * 2. Computes perceptual luminance: L = dot(color.rgb, vec3(0.299, 0.587, 0.114)).
     * 3. Partitions screen space into 2D character cells of size (u_CellSizeX, u_CellSizeY).
     * 4. Maps quantized luminance into the 16x16 ASCII Font Atlas glyph ramp (" .:-=+*#%@").
     * 5. Samples font glyph alpha mask and outputs glowing phosphor/terminal shaded 2D ASCII characters.
     */
    val MESH_3D_ASCII_FRAGMENT_SHADER = """
        precision mediump float;

        uniform sampler2D u_FontAtlas;
        uniform sampler2D u_MeshTexture;
        uniform int u_UseMeshTexture;
        uniform vec2 u_ScreenResolution;
        uniform vec2 u_CellSize;          // Character cell dimensions in pixels (e.g. 16.0, 16.0)
        uniform float u_Time;
        uniform float u_LuminanceScale;
        uniform float u_ScanlineIntensity;
        uniform vec3 u_TerminalColor;     // Phosphor green/amber tint

        varying vec3 v_WorldPos;
        varying vec3 v_Normal;
        varying vec2 v_TexCoord;
        varying vec4 v_Color;
        varying vec3 v_LightDir;
        varying vec3 v_ViewDir;
        varying vec2 v_ScreenCoord;

        // Luminance-to-ASCII ramp indices in 16x16 font atlas:
        // [0: ' ', 1: '.', 2: ':', 3: '-', 4: '=', 5: '+', 6: '*', 7: 'o', 8: '%', 9: 'a', 10: '#', 11: '@', 12: 'M', 13: 'W', 14: '$', 15: '&']
        float getGlyphIndexForLuminance(float lum) {
            float clamped = clamp(lum * u_LuminanceScale, 0.0, 1.0);
            return floor(clamped * 15.0);
        }

        void main() {
            // Normalizing interpolated vectors
            vec3 N = normalize(v_Normal);
            vec3 L = normalize(v_LightDir);
            vec3 V = normalize(v_ViewDir);
            vec3 H = normalize(L + V);

            // 3D Diffuse & Specular Lighting
            float diff = max(dot(N, L), 0.0);
            float spec = pow(max(dot(N, H), 0.0), 16.0) * 0.4;
            float ambient = 0.22;

            // Surface Color
            vec4 surfaceColor = v_Color;
            if (u_UseMeshTexture == 1) {
                surfaceColor *= texture2D(u_MeshTexture, v_TexCoord);
            }

            // Compute total lit RGB
            vec3 litRgb = surfaceColor.rgb * (ambient + diff) + vec3(spec);

            // Calculate Perceptual Surface Luminance
            float luminance = dot(litRgb, vec3(0.299, 0.587, 0.114));

            // Screen-space 2D ASCII Grid Quantization
            vec2 pixelPos = gl_FragCoord.xy;
            vec2 cellIndex = floor(pixelPos / u_CellSize);
            vec2 localCellCoord = fract(pixelPos / u_CellSize);

            // Invert Y for texture coordinates
            localCellCoord.y = 1.0 - localCellCoord.y;

            // Select ASCII character glyph based on luminance
            float glyphIdx = getGlyphIndexForLuminance(luminance);
            float col = mod(glyphIdx, 16.0);
            float row = floor(glyphIdx / 16.0);

            // Calculate UV coordinate in the 16x16 font atlas
            vec2 atlasUv = (vec2(col, row) + localCellCoord) / 16.0;
            vec4 glyphSample = texture2D(u_FontAtlas, atlasUv);
            float glyphAlpha = max(glyphSample.a, glyphSample.r);

            // Background cell shade vs foreground character glyph
            vec3 bgShade = litRgb * 0.12;
            vec3 fgChar = mix(litRgb * 1.3, u_TerminalColor, 0.35);

            vec3 finalColor = mix(bgShade, fgChar, glyphAlpha);

            // CRT Scanline emulation
            if (u_ScanlineIntensity > 0.0) {
                float scanline = sin(gl_FragCoord.y * 3.1415926) * 0.5 + 0.5;
                finalColor *= 1.0 - (u_ScanlineIntensity * 0.22 * (1.0 - scanline));
            }

            gl_FragColor = vec4(finalColor, surfaceColor.a);
        }
    """.trimIndent()

    /**
     * Compiles shader code and returns the OpenGL shader handle.
     */
    fun compileShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) {
            Log.e(TAG, "Error creating shader of type $type")
            return 0
        }

        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compileStatus = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            Log.e(TAG, "Compilation error in shader type $type: $log")
            GLES20.glDeleteShader(shader)
            return 0
        }

        return shader
    }

    /**
     * Creates and links the shader program from vertex and fragment shaders.
     */
    fun createProgram(vertexCode: String = VERTEX_SHADER, fragmentCode: String = FRAGMENT_SHADER): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        if (vertexShader == 0) return 0

        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        if (fragmentShader == 0) {
            GLES20.glDeleteShader(vertexShader)
            return 0
        }

        val program = GLES20.glCreateProgram()
        if (program == 0) {
            Log.e(TAG, "Error creating GL program")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)

        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val log = GLES20.glGetProgramInfoLog(program)
            Log.e(TAG, "Error linking GL program: $log")
            GLES20.glDeleteProgram(program)
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return 0
        }

        // Clean up shaders after successful linking
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)

        return program
    }

    /**
     * Creates and links the 3D Mesh to 2D ASCII Luminance Shader Program.
     */
    fun createMesh3dProgram(): Int {
        return createProgram(MESH_3D_ASCII_VERTEX_SHADER, MESH_3D_ASCII_FRAGMENT_SHADER)
    }
}
