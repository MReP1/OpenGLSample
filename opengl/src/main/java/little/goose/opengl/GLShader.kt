package little.goose.opengl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class OESShader : GLShader(OES_FRAGMENT_SHADER_CODE) {
    override val target: Int = GLES11Ext.GL_TEXTURE_EXTERNAL_OES
}

class RGBShader : GLShader(RGB_FRAGMENT_SHADER_CODE) {
    override val target: Int = GLES30.GL_TEXTURE_2D
}

sealed class GLShader(
    fragmentShader: String
) {

    private var programId: Int

    abstract val target: Int

    init {
        // Load and compile shader code.
        val vertexShaderId = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        val fragmentShaderId = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(vertexShaderId, VERTEX_SHADER_STRING)
        GLES30.glShaderSource(fragmentShaderId, fragmentShader)
        GLES30.glCompileShader(vertexShaderId)
        GLES30.glCompileShader(fragmentShaderId)

        val compileStatus = intArrayOf(GLES30.GL_FALSE)
        GLES30.glGetShaderiv(vertexShaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES30.GL_TRUE) {
            throw RuntimeException(GLES30.glGetShaderInfoLog(vertexShaderId))
        }
        GLES30.glGetShaderiv(fragmentShaderId, GLES30.GL_COMPILE_STATUS, compileStatus, 0)
        if (compileStatus[0] != GLES30.GL_TRUE) {
            throw RuntimeException(GLES30.glGetShaderInfoLog(fragmentShaderId))
        }

        programId = GLES30.glCreateProgram()
        require(programId != 0) {
            "create program failed. error: ${GLES30.glGetError()}"
        }

        // Attach shader to GL program.
        GLES30.glAttachShader(programId, vertexShaderId)
        GLES30.glAttachShader(programId, fragmentShaderId)
        // Link GL program.
        GLES30.glLinkProgram(programId)
        val link = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, link, 0)
        require(link[0] == GLES30.GL_TRUE) {
            "link program failed. status: ${link[0]}, message: ${
                GLES30.glGetProgramInfoLog(programId)
            }"
        }

        // Use GL program.
        GLES30.glUseProgram(programId)
        // Delete shader
        GLES30.glDeleteShader(vertexShaderId)
        GLES30.glDeleteShader(fragmentShaderId)
    }

    fun drawFrom(
        textureId: Int,
        viewportX: Int,
        viewPortY: Int,
        viewPortWidth: Int,
        viewPortHeight: Int,
        matrix: FloatArray = emptyMtx,
        clear: Boolean = true,
    ) {
        if (programId == -1) return
        GLES30.glUseProgram(programId)
        // Enable vertex
        GLES30.glEnableVertexAttribArray(glPositionId)
        GLES30.glEnableVertexAttribArray(glAttribTextureCoordinate)
        // Assign render pointer. Always render full screen.
        GLES30.glVertexAttribPointer(
            glPositionId, 2, GLES30.GL_FLOAT, false, 0, FULL_RECTANGLE_BUF
        )
        GLES30.glVertexAttribPointer(
            glAttribTextureCoordinate, 2, GLES30.GL_FLOAT, false, 0, FULL_RECTANGLE_TEX_BUF
        )
        // Bind texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(target, textureId)

        val glInputTextureId = GLES30.glGetUniformLocation(programId, "inputImageTexture")
        GLES30.glUniform1i(glInputTextureId, 0)

        val glTexTransformId = GLES30.glGetUniformLocation(programId, "textureTransform")
        GLES30.glUniformMatrix4fv(glTexTransformId, 1, false, matrix, 0)

        // Viewport
        GLES30.glViewport(viewportX, viewPortY, viewPortWidth, viewPortHeight)
        // Clear Screen
        if (clear) {
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        }
        // Draw texture
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        // Unbind texture
        GLES30.glBindTexture(target, 0)
        // Disable vertex
        GLES30.glDisableVertexAttribArray(glPositionId)
        GLES30.glDisableVertexAttribArray(glAttribTextureCoordinate)
    }

    fun release() {
        if (programId != -1) {
            GLES30.glDeleteProgram(programId)
            programId = -1
        }
    }

    companion object {

        // Vertex
        private const val glPositionId = 0
        private const val glAttribTextureCoordinate = 1

        private val VERTEX_SHADER_STRING = """
            #version 300 es
            precision mediump float;
            layout(location = 0) in vec4 position;
            layout(location = 1) in vec4 inputTextureCoordinate;
            uniform mat4 textureTransform;
            out vec2 textureCoordinate;
            void main()
            {
                gl_Position = position;
                textureCoordinate = (textureTransform * inputTextureCoordinate).xy;
            }    
        """.trimIndent()

        val OES_FRAGMENT_SHADER_CODE = """
            #version 300 es
            #extension GL_OES_EGL_image_external_essl3: require
            precision mediump float;
            layout(location = 0) uniform samplerExternalOES inputImageTexture;
            in highp vec2 textureCoordinate;
            out vec4 fragColor;
            void main()
            {
                fragColor = texture(inputImageTexture, textureCoordinate);
            }
        """.trimIndent()

        val RGB_FRAGMENT_SHADER_CODE = """
            #version 300 es
            precision mediump float;
            in highp vec2 textureCoordinate;
            uniform sampler2D inputImageTexture;
            out vec4 fragColor;
            void main()
            {
                fragColor = texture(inputImageTexture, textureCoordinate);
            }
        """.trimIndent()

        private val FULL_RECTANGLE_BUF = floatArrayOf(
            -1.0f, -1.0f,  // Bottom left.
            1.0f, -1.0f,   // Bottom right.
            -1.0f, 1.0f,   // Top left.
            1.0f, 1.0f     // Top right.
        ).toFloatBuffer()

        private val FULL_RECTANGLE_TEX_BUF = floatArrayOf(
            0.0f, 0.0f,    // Bottom left.
            1.0f, 0.0f,    // Bottom right.
            0.0f, 1.0f,    // Top left.
            1.0f, 1.0f     // Top right.
        ).toFloatBuffer()

        private fun FloatArray.toFloatBuffer(): FloatBuffer {
            return ByteBuffer.allocateDirect(this.size * Float.SIZE_BITS)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().put(this)
                .apply { position(0) }
        }

        private val emptyMtx = floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )
    }
}