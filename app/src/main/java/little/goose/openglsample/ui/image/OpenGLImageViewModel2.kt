package little.goose.openglsample.ui.image

import android.app.Application
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.lifecycle.AndroidViewModel
import little.goose.opengl.EglSystem
import little.goose.opengl.GLUtils
import little.goose.opengl.RGBShader
import little.goose.openglsample.R
import java.nio.ByteBuffer

class OpenGLImageViewModel2(application: Application) : AndroidViewModel(application) {

    private val bitmap = BitmapFactory.decodeResource(application.resources, R.drawable.cat)
    private val byteBuffer = ByteBuffer.allocateDirect(bitmap.byteCount).apply {
        bitmap.copyPixelsToBuffer(this)
        rewind()
    }

    private val shader by lazy { RGBShader() }

    private var eglSystem: EglSystem? = null
    private var imageId = 0

    fun loadLittleCatImage(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        eglSystem = EglSystem.createSurface(surfaceTexture)
        eglSystem?.withCurrent {
            imageId = GLUtils.generate2DTextureId(bitmap.width, bitmap.height, byteBuffer)
            shader.drawFrom(imageId, 0, 0, width, height)
            eglSystem?.swapBuffers()
            byteBuffer.clear()
            bitmap.recycle()
            shader.release()
            GLUtils.deleteTexture(imageId)
        }
        eglSystem?.release()
        eglSystem = null
    }

}