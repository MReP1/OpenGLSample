package little.goose.openglsample.ui.image

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.opengl.*
import android.opengl.EGLExt.EGL_RECORDABLE_ANDROID
import androidx.lifecycle.AndroidViewModel
import little.goose.openglsample.R
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class OpenGLImageViewModel(application: Application) : AndroidViewModel(application) {

    private val bitmap = BitmapFactory.decodeResource(application.resources, R.drawable.cat)
    private val byteBuffer = ByteBuffer.allocateDirect(bitmap.byteCount).apply {
        bitmap.copyPixelsToBuffer(this)
        rewind()
    }

    private var eglDisplay: EGLDisplay? = EGL14.EGL_NO_DISPLAY
    private var eglSurface: EGLSurface? = EGL14.EGL_NO_SURFACE
    private var eglContext: EGLContext? = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null

    private var textureId = 0
    private var programId = 0

    fun loadImage(surfaceTexture: SurfaceTexture) {
        // 获取EGLDisplay
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)

        // 初始化EGLDisplay
        val versions = IntArray(2)
        EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)

        // 选择配置
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(
            eglDisplay, attributesForSurface, 0,
            configs, 0,
            configs.size,
            numConfigs, 0
        )
        eglConfig = configs[0]

        // 创建GL上下文
        val attribList = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT, attribList, 0
        )

        // 创建EGLSurface
        eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, surfaceTexture, intArrayOf(EGL14.EGL_NONE), 0
        )

        // 进入GL上下文
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        // 创建Texture_2D纹理
        val textures = IntArray(1)
        val target = GLES30.GL_TEXTURE_2D
        GLES30.glGenTextures(textures.size, textures, 0)
        GLES30.glBindTexture(target, textures[0])
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        textureId = textures[0]

        // 加载图片数据
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, bitmap.width, bitmap.height,
            0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, byteBuffer
        )

        // 加载顶点着色器和片段着色器
        val vertexShaderId = GLES30.glCreateShader(GLES30.GL_VERTEX_SHADER)
        val fragmentShaderId = GLES30.glCreateShader(GLES30.GL_FRAGMENT_SHADER)
        GLES30.glShaderSource(vertexShaderId, VERTEX_SHADER_STRING)
        GLES30.glShaderSource(fragmentShaderId, RGB_FRAGMENT_SHADER_CODE)
        GLES30.glCompileShader(vertexShaderId)
        GLES30.glCompileShader(fragmentShaderId)

        // 创建OpenGL程序
        programId = GLES30.glCreateProgram()

        // 将着色器附加到OpenGL程序
        GLES30.glAttachShader(programId, vertexShaderId)
        GLES30.glAttachShader(programId, fragmentShaderId)

        // 关联OpenGL程序
        GLES30.glLinkProgram(programId)

        // 设置OpenGL程序为当前渲染程序
        GLES30.glUseProgram(programId)

        // 清除着色器对象
        GLES30.glDeleteShader(vertexShaderId)
        GLES30.glDeleteShader(fragmentShaderId)

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

        // 绑定纹理
        val emptyMtx = floatArrayOf(
            1.0f, 0.0f, 0.0f, 0.0f,
            0.0f, 1.0f, 0.0f, 0.0f,
            0.0f, 0.0f, 1.0f, 0.0f,
            0.0f, 0.0f, 0.0f, 1.0f
        )
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, textureId)

        // 将纹理单元传入着色器uniform变量
        val glInputTextureId = GLES30.glGetUniformLocation(programId, "inputImageTexture")
        GLES30.glUniform1i(glInputTextureId, 0)

        // 将矩阵传入着色器uniform变量
        val glTexTransformId = GLES30.glGetUniformLocation(programId, "textureTransform")
        GLES30.glUniformMatrix4fv(glTexTransformId, 1, false, emptyMtx, 0)

        // 绘制纹理
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)

        // 解绑纹理
        GLES30.glBindTexture(target, 0)

        // 禁用顶点着色器
        GLES30.glDisableVertexAttribArray(glPositionId)
        GLES30.glDisableVertexAttribArray(glAttribTextureCoordinate)

        // 绘制图像上屏
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)

        // 释放图片资源
        byteBuffer.clear()
        bitmap.recycle()

        // 释放OpenGL程序资源
        GLES30.glDeleteProgram(programId)

        // 清除纹理资源
        GLES30.glDeleteTextures(1, intArrayOf(textureId), 0)

        // 清除Surface
        EGL14.eglDestroySurface(eglDisplay, eglSurface)
        eglSurface = EGL14.EGL_NO_SURFACE

        // 离开GL上下文
        EGL14.eglMakeCurrent(
            eglDisplay,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_SURFACE,
            EGL14.EGL_NO_CONTEXT
        )
        // 清除GL上下文资源
        EGL14.eglDestroyContext(eglDisplay, eglContext)
        eglContext = EGL14.EGL_NO_CONTEXT

        // 释放与该线程关联的EGL资源
        EGL14.eglReleaseThread()
        // 释放EGL与显示设备或窗口系统的链接，释放与EGL关联的资源
        EGL14.eglTerminate(eglDisplay)
    }

    companion object {

        private val attributesForSurface = intArrayOf(
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT or EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )

        // 顶点着色器
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

        private const val glPositionId = 0
        private const val glAttribTextureCoordinate = 1

        // 片段着色器
        private val RGB_FRAGMENT_SHADER_CODE = """
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

        val FULL_RECTANGLE_BUF = floatArrayOf(
            -1.0f, -1.0f,  // Bottom left.
            1.0f, -1.0f,   // Bottom right.
            -1.0f, 1.0f,   // Top left.
            1.0f, 1.0f     // Top right.
        ).toFloatBuffer()

        val FULL_RECTANGLE_TEX_BUF = floatArrayOf(
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

    }

}