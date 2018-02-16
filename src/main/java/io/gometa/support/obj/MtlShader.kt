/*
 * Copyright (c) 2018 GoMeta Inc. All Rights Reserver
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gometa.support.obj

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.support.annotation.CallSuper
import de.javagl.obj.FloatTuple
import de.javagl.obj.Mtl
import de.javagl.obj.TextureOptions
import io.gometa.support.obj.mlt.MtlShaderImpl
import timber.log.Timber

/**
 *
 */
abstract class MtlShader(
    protected val context: Context,
    protected val mtl: Mtl
) {

    companion object {
        fun newShader(context: Context, mtl: Mtl): MtlShader = MtlShaderImpl(context, mtl)
//                when (mtl.illuminationMode) {
//                    Mtl.IlluminationMode.COLOR_ON_AMBIENT_OFF -> MtlShaderIllum0(context, mtl)
//                    Mtl.IlluminationMode.COLOR_ON_AMBIENT_ON -> MtlShaderIllum1(context, mtl)
//                    Mtl.IlluminationMode.HIGHLIGHT_ON -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.REFLECTION_ON_RAY_TRACE_ON -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.TRANSPARENCY_GLASS_ON_REFLECTION_RAY_TRACE_ON -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.REFLECTION_FRESNEL_ON_RAY_TRACE_ON -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.TRANSPARENCY_REFRACTION_ON_REFLECTION_FRESNEL_OFF_RAY_TRACE_ON -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.TRANSPARENCY_REFRACTION_ON_REFLECTION_FRESNEL_ON_RAY_TRACE_ON -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.REFLECTION_ON_RAY_TRACE_OFF -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.TRANSPARENCY_GLASS_ON_REFLECTION_RAY_TRACE_OFF -> MtlShaderIllum2(context, mtl)
//                    Mtl.IlluminationMode.SHADOW_ON_INVISIBLE_SURFACES -> MtlShaderIllum2(context, mtl)
//                }
    }
    private var owningThreadId: Long? = null

    open protected val vertexShaderResId: Int = R.raw.object_vertex

    abstract protected val fragmentShaderResId: Int

    protected var programHandle = 0
        private set

    private var modelViewUniform = 0
    private var modelViewProjectionUniform = 0

    var positionAttribute = 0
        private set
    var normalAttribute = 0
        private set
    var texCoordAttribute = 0
        private set

    private val scaleMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    protected val modelViewMatrix = FloatArray(16)
    protected val modelViewProjectionMatrix = FloatArray(16)

    fun createOnGlThread() {
        if (owningThreadId == Thread.currentThread().id) {
            // Already initialized for this GL context
            return
        } else if (owningThreadId != null) {
            Timber.w("This renderer is owned by another GL context")
        }

        allocateTextures()

        val vertexShader = VirtualObject.readRawResource(context, vertexShaderResId)?.let {
            VirtualObject.compileShader(GLES20.GL_VERTEX_SHADER, it)
        } ?: throw RuntimeException("Failed to load vertex shader")
        val fragmentShader = VirtualObject.readRawResource(context, fragmentShaderResId)?.let {
            VirtualObject.compileShader(GLES20.GL_FRAGMENT_SHADER, it)
        } ?: throw RuntimeException("Failed to load fragment shader")

        // Create and link program
        programHandle = VirtualObject.createAndLinkProgram(vertexShader, fragmentShader)

        modelViewUniform = GLES20.glGetUniformLocation(programHandle, "u_ModelView")
        modelViewProjectionUniform = GLES20.glGetUniformLocation(programHandle, "u_ModelViewProjection")

        positionAttribute = GLES20.glGetAttribLocation(programHandle, "a_Position")
        normalAttribute = GLES20.glGetAttribLocation(programHandle, "a_Normal")
        texCoordAttribute = GLES20.glGetAttribLocation(programHandle, "a_TexCoord")

        onProgramCreated()

        owningThreadId = Thread.currentThread().id
    }

    fun updateModelMatrix(anchorMatrix: FloatArray, scaleFactor: Float) {
        Matrix.setIdentityM(scaleMatrix, 0)
        scaleMatrix[0] = scaleFactor
        scaleMatrix[5] = scaleFactor
        scaleMatrix[10] = scaleFactor
        Matrix.multiplyMM(modelMatrix, 0, anchorMatrix, 0, scaleMatrix, 0)
    }

    @CallSuper
    open fun preDraw(viewMatrix: FloatArray, projectionMatrix: FloatArray,
        lightingParameters: LightingParameters) {
        // Make sure that we're created for this thread
        createOnGlThread()

        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        GLES20.glUseProgram(programHandle)

        // Set the ModelViewProjection matrix in the vertex shader
        GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0)
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0)
    }

    fun destroy() {
        if (owningThreadId != Thread.currentThread().id) {
            Timber.w("Calling destroy() from non-owning thread")
            return
        }

        GLES20.glDeleteProgram(programHandle)

        deleteTextures()

        owningThreadId = null
    }

    open fun postDraw() {}

    open protected fun allocateTextures() {}

    open protected fun onProgramCreated() {}

    open protected fun deleteTextures() {}

    protected fun allocateTexture(opt: TextureOptions, textureId: Int): Int {
        val textures = IntArray(1)
        GLES20.glActiveTexture(textureId)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0])
        val bitmap = BitmapFactory.decodeFile(opt.fileName)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        bitmap.recycle()
        return textures[0]
    }

    protected fun deleteTexture(vararg textureHandles: Int) {
        textureHandles
            .filter { it > 0 }
            .takeIf { it.isNotEmpty() }
            ?.let {
                GLES20.glDeleteTextures(it.size, it.toIntArray(), 0)
            }
    }

    protected fun bindTextureForDraw(opt: TextureOptions?, textureId: Int, textureUniform: Int,
        textureIndex: Int, textureHandle: Int, useTextureHandle: Int, mtlHandle: Int,
        mtlColor: FloatTuple) {
        opt?.let {
            GLES20.glActiveTexture(textureId)
            GLES20.glUniform1i(textureUniform, textureIndex)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
            GLES20.glUniform1i(useTextureHandle, 1)
        } ?: run {
            GLES20.glUniform1i(useTextureHandle, 0)
            GLES20.glUniform3f(mtlHandle, mtlColor.x, mtlColor.y, mtlColor.z)
        }
    }

}