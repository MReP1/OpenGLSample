package little.goose.opengl

import android.opengl.GLES11Ext
import android.opengl.GLES30
import java.nio.Buffer

object GLUtils {

    fun generateOESTextureId(): Int = generateTextureID(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)

    fun generate2DTextureId(width: Int, height: Int, pixels: Buffer? = null): Int {
        val textureId = generateTextureID(GLES30.GL_TEXTURE_2D)
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, width, height,
            0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, pixels
        )
        return textureId
    }

    fun generateFrameBuffer(textureId: Int): Int {
        val frameBufferArray = IntArray(1)
        GLES30.glGenFramebuffers(1, frameBufferArray, 0)
        val frameBufferId = frameBufferArray[0]
        // 绑定帧缓冲区
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBufferId)
        // 将2D纹理附着到帧缓冲对象
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, textureId, 0
        )
        // 检查帧缓冲区是否完整
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) return -1
        return frameBufferId
    }

    private fun generateTextureID(target: Int): Int {
        val textures = IntArray(1)
        GLES30.glGenTextures(textures.size, textures, 0)
        GLES30.glBindTexture(target, textures[0])
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(target, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return textures[0]
    }

    fun bindFrameBuffer(frameBufferId: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, frameBufferId)
    }

    fun deleteTexture(vararg textureIds: Int) {
        GLES30.glDeleteTextures(textureIds.size, textureIds, 0)
    }

    fun deleteFrameBuffer(vararg bufferIds: Int) {
        GLES30.glDeleteBuffers(bufferIds.size, bufferIds, 0)
    }

}