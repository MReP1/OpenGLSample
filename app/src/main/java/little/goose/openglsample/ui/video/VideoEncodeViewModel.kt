package little.goose.openglsample.ui.video

import android.app.Application
import android.graphics.BitmapFactory
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import little.goose.opengl.EglSystem
import little.goose.opengl.GLUtils
import little.goose.openglsample.R
import little.goose.openglsample.logic.video.VideoEncoder
import little.goose.openglsample.logic.video.VideoEncoderConfig
import java.io.File
import java.nio.ByteBuffer

class VideoEncodeViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val encoder = VideoEncoder()
    private val bitmap = BitmapFactory.decodeResource(application.resources, R.drawable.cat)
    private val byteBuffer = ByteBuffer.allocateDirect(bitmap.byteCount).apply {
        bitmap.copyPixelsToBuffer(this)
        rewind()
    }

    init {
        viewModelScope.launch {
            encoder.init(
                VideoEncoderConfig(
                    outPutPath = File(
                        getApplication<Application>().getExternalFilesDir("Video"),
                        "test.mp4"
                    ).path
                )
            )
            for (i in 0 until 100) {
                encoder.withGlContext {
                    val texture =
                        GLUtils.generate2DTextureId(bitmap.width, bitmap.height, byteBuffer)
                    encoder.encodeFrame(texture, bitmap.width, bitmap.height /*FIXME*/)
                }
            }
        }
    }

}