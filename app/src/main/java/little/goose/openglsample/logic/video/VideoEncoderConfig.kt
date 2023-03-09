package little.goose.openglsample.logic.video

import android.media.MediaFormat

data class VideoEncoderConfig(
    val outPutPath: String,
    val width: Int = 640,
    val height: Int = 480,
    val mimeType: String = MediaFormat.MIMETYPE_VIDEO_AVC,
    val bitRate: Int = 500_000,
    val frameRate: Int = 30,
    val iFrameInterval: Int = 15
)
