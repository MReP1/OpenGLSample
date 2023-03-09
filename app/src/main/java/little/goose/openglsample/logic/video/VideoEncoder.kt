package little.goose.openglsample.logic.video

import android.media.MediaCodec
import android.media.MediaCodec.BufferInfo
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.android.HandlerDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import little.goose.opengl.EglSystem
import little.goose.opengl.RGBShader
import java.io.File
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class VideoEncoder {

    enum class State {
        Uninitialized, RUNNING, DESTROYED
    }

    @Volatile
    private var state: State = State.Uninitialized

    private var isMuxerStated = false
    private var trackIndex = 0

    private var _mediaCodec: MediaCodec? = null
    private val mediaCodec get() = _mediaCodec!!

    private var _eglSystem: EglSystem? = null
    private val eglSystem get() = _eglSystem!!

    private var _shader: RGBShader? = null
    private val shader get() = _shader!!

    private var _encoderThread: HandlerThread? = null
    private val encoderThread get() = _encoderThread!!

    private var _encoderHandler: Handler? = null
    private val encoderHandler get() = _encoderHandler!!

    private var _handlerDispatcher: HandlerDispatcher? = null
    private val handlerDispatcher get() = _handlerDispatcher!!

    private var _coroutineScope: CoroutineScope? = null
    private val coroutineScope get() = _coroutineScope!!

    private var _mediaMuxer: MediaMuxer? = null
    private val mediaMuxer: MediaMuxer get() = _mediaMuxer!!

    private val bufferInfo by lazy { BufferInfo() }

    private val exceptionHandler by lazy {
        CoroutineExceptionHandler { _, exc -> Log.e("VideoEncoder", exc.message.toString()) }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun init(config: VideoEncoderConfig) = suspendCoroutine { cont ->
        val thread = HandlerThread("VideoEncoder").apply { start() }.also { _encoderThread = it }
        val handler = Handler(thread.looper).also { _encoderHandler = it }
        val dispatcher = handler.asCoroutineDispatcher().also { _handlerDispatcher = it }
        val scope = CoroutineScope(dispatcher + exceptionHandler).also { _coroutineScope = it }
        scope.launch(dispatcher) {
            val codec = MediaCodec.createEncoderByType(config.mimeType).also { _mediaCodec = it }
            val mediaFormat = MediaFormat.createVideoFormat(
                config.mimeType, config.width, config.height
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.frameRate)
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.iFrameInterval)
            }
            codec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            _eglSystem = EglSystem.createSurface(codec.createInputSurface())
            codec.start()
            val file = File(config.outPutPath)
            if (!file.exists()) {
                file.createNewFile()
            }
            _mediaMuxer = MediaMuxer(config.outPutPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            isMuxerStated = false
            state = State.RUNNING
            cont.resume(Unit)
        }
    }

    fun withGlContext(action: () -> Unit) {
        eglSystem.withCurrent { action() }
    }

    fun encodeFrame(texture: Int, width: Int, height: Int, timestamp: Long) {
        if (state != State.RUNNING) return
        coroutineScope.launch(handlerDispatcher) {
            if (state != State.RUNNING) return@launch
            eglSystem.makeCurrent()
            shader.drawFrom(texture, 0, 0, width, height)
            eglSystem.setTimestamp(timestamp)
            eglSystem.swapBuffers()
            dequeueEncode()
        }
    }

    fun stopEncode() {
        if (state != State.RUNNING) return
        coroutineScope.launch(handlerDispatcher) {
            if (state != State.RUNNING) return@launch
            mediaCodec.signalEndOfInputStream()
        }
    }

    private fun dequeueEncode() {
        var index = mediaCodec.dequeueOutputBuffer(bufferInfo, 1_000_000)
        while (index >= 0) {
            if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                mediaCodec.releaseOutputBuffer(index, false)
                continue
            } else if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                break
            }
            mediaCodec.getOutputBuffer(index)?.let { outputBuffer ->
                handleEncodeVideoFrame(mediaCodec.outputFormat, outputBuffer, bufferInfo)
            }
            mediaCodec.releaseOutputBuffer(index, false)
            index = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
        }
    }

    private fun handleEncodeVideoFrame(
        format: MediaFormat,
        outputBuffer: ByteBuffer,
        bufferInfo: BufferInfo
    ) {
        if (!isMuxerStated) {
            trackIndex = mediaMuxer.addTrack(format)
            mediaMuxer.start()
            isMuxerStated = true
        }
        outputBuffer.position(bufferInfo.offset)
        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
        mediaMuxer.writeSampleData(trackIndex, outputBuffer, bufferInfo)
    }

    suspend fun release() {
        state = State.DESTROYED
        suspendCoroutine { cont ->
            _coroutineScope?.launch(handlerDispatcher) {
                _shader?.release()
                _shader = null
                _eglSystem?.release()
                _eglSystem = null
                _mediaCodec?.apply {
                    stop()
                    release()
                }
                _mediaCodec = null
                _mediaMuxer?.apply {
                    stop()
                    release()
                }
                cont.resume(Unit)
            }
        }
        isMuxerStated = false
        _coroutineScope?.cancel()
        _coroutineScope = null
        _handlerDispatcher?.cancel()
        _handlerDispatcher = null
        _encoderHandler?.removeCallbacksAndMessages(null)
        _encoderHandler = null
        _encoderThread?.quit()
        _encoderThread = null
    }
}