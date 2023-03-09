package little.goose.opengl

import android.graphics.SurfaceTexture
import android.opengl.*
import android.view.Surface

class EglSystem(
    width: Int = 0,
    height: Int = 0,
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT,
    private var eglConfig: EGLConfig? = null,
    surface: Any? = null,
    glVersion: Int = 3
) {

    private var eglSurface: EGLSurface
    private var eglDisplay: EGLDisplay

    companion object {
        /**
         * Create offscreen surface EGL
         *
         * @param width
         * @param height
         * @param eglContext
         */
        fun createEGLSurface(
            width: Int = 1, height: Int = 1,
            eglContext: EGLContext = EGL14.EGL_NO_CONTEXT, glVersion: Int = 3
        ) = EglSystem(width, height, eglContext, null, null, glVersion)

        /**
         * Create with surface EGL
         *
         * @param surface
         * @param eglContext
         */
        fun createSurface(
            surface: Surface,
            eglContext: EGLContext = EGL14.EGL_NO_CONTEXT, glVersion: Int = 3
        ) = EglSystem(0, 0, eglContext, null, surface, glVersion)

        fun createSurface(
            surfaceTexture: SurfaceTexture,
            eglContext: EGLContext = EGL14.EGL_NO_CONTEXT, glVersion: Int = 3
        ) = EglSystem(0, 0, eglContext, null, surfaceTexture, glVersion)

        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }

    fun makeCurrent(): Boolean {
        return EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    fun detachCurrent() = EGL14.eglMakeCurrent(
        eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
    )

    fun swapBuffers() = EGL14.eglSwapBuffers(eglDisplay, eglSurface)

    fun getEglContext() = eglContext

    private fun getEGLErrorMessage(): String {
        return Integer.toHexString(EGL14.eglGetError())
    }

    inline fun withCurrent(action: (EglSystem) -> Unit) {
        makeCurrent()
        action(this)
        detachCurrent()
    }

    fun setTimestamp(timestamp: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, timestamp)
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            if (eglSurface != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = EGL14.EGL_NO_SURFACE
            }
            EGL14.eglMakeCurrent(
                eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT
            )
            if (eglContext != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = EGL14.EGL_NO_CONTEXT
            }
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        eglDisplay = EGL14.EGL_NO_DISPLAY
    }

    private val attributesForSurface = intArrayOf(
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_DEPTH_SIZE, 0,
        EGL14.EGL_STENCIL_SIZE, 0,
        EGL14.EGL_RENDERABLE_TYPE, if (glVersion == 2)
            EGL14.EGL_OPENGL_ES2_BIT else EGL14.EGL_OPENGL_ES2_BIT or EGLExt.EGL_OPENGL_ES3_BIT_KHR,
        EGL_RECORDABLE_ANDROID, 1,
        EGL14.EGL_NONE
    )

    private val attributesForOffscreenSurface = intArrayOf(
        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,  //前台显示Surface这里EGL10.EGL_WINDOW_BIT
        EGL14.EGL_RED_SIZE, 8,
        EGL14.EGL_GREEN_SIZE, 8,
        EGL14.EGL_BLUE_SIZE, 8,
        EGL14.EGL_ALPHA_SIZE, 8,
        EGL14.EGL_DEPTH_SIZE, 0,
        EGL14.EGL_STENCIL_SIZE, 0,
        EGL14.EGL_RENDERABLE_TYPE, if (glVersion == 2)
            EGL14.EGL_OPENGL_ES2_BIT else EGL14.EGL_OPENGL_ES2_BIT or EGLExt.EGL_OPENGL_ES3_BIT_KHR,
        EGL_RECORDABLE_ANDROID, 1,
        EGL14.EGL_NONE
    )

    init {
        // getDisplay
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        require(eglDisplay != EGL14.EGL_NO_DISPLAY) {
            "unable to get EGL14 display"
        }

        // check version
        val versions = IntArray(2)
        require(EGL14.eglInitialize(eglDisplay, versions, 0, versions, 1)) {
            eglDisplay = EGL14.EGL_NO_DISPLAY
            "unable to initialize EGL14"
        }

        // choose config
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        require(
            EGL14.eglChooseConfig(
                eglDisplay,
                if (surface == null) attributesForOffscreenSurface else attributesForSurface,
                0,
                configs, 0, configs.size,
                numConfigs, 0
            )
        ) { "unable to choose config" }
        eglConfig = configs[0]

        // generate context
        eglContext = if (eglContext == EGL14.EGL_NO_CONTEXT) {
            val attribList = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, glVersion, EGL14.EGL_NONE)
            EGL14.eglCreateContext(eglDisplay, eglConfig, eglContext, attribList, 0)
        } else eglContext
        require(eglContext != EGL14.EGL_NO_CONTEXT) {
            "EGL error: ${EGL14.eglGetError()}"
        }

        // create surface
        require(surface == null || surface is Surface || surface is SurfaceTexture) {
            "surface mast be Surface or SurfaceTexture"
        }
        eglSurface = if (surface == null) {
            EGL14.eglCreatePbufferSurface(
                eglDisplay, eglConfig, intArrayOf(
                    EGL14.EGL_WIDTH, width, EGL14.EGL_HEIGHT, height, EGL14.EGL_NONE
                ), 0
            )
        } else {
            EGL14.eglCreateWindowSurface(
                eglDisplay, eglConfig, surface, intArrayOf(EGL14.EGL_NONE), 0
            )
        }
    }
}