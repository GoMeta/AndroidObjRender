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
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.bumptech.glide.Glide
import de.javagl.obj.Mtl
import de.javagl.obj.TextureOptions
import io.gometa.support.obj.VirtualObject.Companion.normalizeVec3
import timber.log.Timber

/**
 *
 */
class MtlRenderer(
    private val context: Context,
    private val mtl: Mtl
) {

    private val textureOptionMap = HashMap<TextureOptions, Int>()
    private var programHandle = 0

    private var modelViewUniform = 0
    private var modelViewProjectionUniform = 0
    private var illuminationModeUniform = 0
    private var lightingParameterUniform = 0
    private var materialParametersUniform = 0
    private var materialAmbientLightingUniform = 0
    private var materialDiffuseLightingUniform = 0
    private var materialSpecularLightingUniform = 0
    private var textureUniform = 0
    private var textureValidUniform = 0

    var positionAttribute = 0
        private set
    var normalAttribute = 0
        private set
    var texCoordAttribute = 0
        private set

    private val scaleMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val modelViewMatrix = FloatArray(16)
    private val modelViewProjectionMatrix = FloatArray(16)
    private val viewLightDirection = FloatArray(4)

    private var owningThreadId: Long? = null

    fun createOnGlThread() {
        if (owningThreadId == Thread.currentThread().id) {
            // Already initialized for this GL context
            return
        } else if (owningThreadId != null) {
            Timber.w("This renderer is owned by another GL context")
        }

        // Allocate the textures
        arrayOf(mtl.mapKa, mtl.mapKd, mtl.mapKs, mtl.mapNs, mtl.mapD, mtl.bumpMap,
            mtl.displacementMap, mtl.decalMap)
            .filterNotNull()
            .takeIf { it.isNotEmpty() }
            ?.let { maps ->
                val textures = IntArray(maps.size)
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glGenTextures(textures.size, textures, 0)
                maps.forEachIndexed { index, textureOptions ->
                    textureOptionMap[textureOptions] = textures[index]
                    createTexture(textureOptions, textures[index])
                }
            }

        // Load the shaders
        val vertexShader = VirtualObject.readRawResource(context, R.raw.object_vertex)?.let {
            VirtualObject.compileShader(GLES20.GL_VERTEX_SHADER, it)
        } ?: throw RuntimeException("Failed to load vertex shader")
        val fragmentShader = VirtualObject.readRawResource(context, R.raw.object_fragment)?.let {
            VirtualObject.compileShader(GLES20.GL_FRAGMENT_SHADER, it)
        } ?: throw RuntimeException("Failed to load fragment shader")

        // Create and link program
        programHandle = VirtualObject.createAndLinkProgram(vertexShader, fragmentShader)

        GLES20.glUseProgram(programHandle)
        modelViewUniform = GLES20.glGetUniformLocation(programHandle, "u_ModelView")
        modelViewProjectionUniform = GLES20.glGetUniformLocation(programHandle, "u_ModelViewProjection")
        illuminationModeUniform = GLES20.glGetUniformLocation(programHandle, "u_IlluminationMode")
        textureUniform = GLES20.glGetUniformLocation(programHandle, "u_Texture")
        textureValidUniform = GLES20.glGetUniformLocation(programHandle, "u_TextureValid")
        lightingParameterUniform = GLES20.glGetUniformLocation(programHandle, "u_LightingParameters")
        materialParametersUniform = GLES20.glGetUniformLocation(programHandle, "u_MaterialParameters")
        materialAmbientLightingUniform = GLES20.glGetUniformLocation(programHandle, "u_MaterialAmbientLighting")
        materialDiffuseLightingUniform = GLES20.glGetUniformLocation(programHandle, "u_MaterialDiffuseLighting")
        materialSpecularLightingUniform = GLES20.glGetUniformLocation(programHandle, "u_MaterialSpecularLighting")

        positionAttribute = GLES20.glGetAttribLocation(programHandle, "a_Position")
        normalAttribute = GLES20.glGetAttribLocation(programHandle, "a_Normal")
        texCoordAttribute = GLES20.glGetAttribLocation(programHandle, "a_TexCoord")

        owningThreadId = Thread.currentThread().id
    }

    fun updateModelMatrix(anchorMatrix: FloatArray, scaleFactor: Float) {
        Matrix.setIdentityM(scaleMatrix, 0)
        scaleMatrix[0] = scaleFactor
        scaleMatrix[5] = scaleFactor
        scaleMatrix[10] = scaleFactor
        Matrix.multiplyMM(modelMatrix, 0, anchorMatrix, 0, scaleMatrix, 0)
    }

    fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray,
        lightingParameters: LightingParameters) {
        createOnGlThread()
        Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(modelViewProjectionMatrix, 0, projectionMatrix, 0, modelViewMatrix, 0)

        GLES20.glUseProgram(programHandle)

        // Set the lighting environment properties
        Matrix.multiplyMV(viewLightDirection, 0, modelViewMatrix, 0,
            lightingParameters.lightDirection.array, 0)
        normalizeVec3(viewLightDirection)
        GLES20.glUniform4f(lightingParameterUniform,
            viewLightDirection[0], viewLightDirection[1], viewLightDirection[2],
            lightingParameters.lightIntensity)

        GLES20.glUniform1i(illuminationModeUniform, mtl.illuminationMode.intValue)

        // TODO This is crude at best and only handles map_Kd for textures, the majority of the work
        // will need to go here.
        mtl.mapKd?.let(this::loadTexture) ?: run {
            GLES20.glUniform1i(textureValidUniform, 0)

            // Set the object material properties
            GLES20.glUniform2f(materialParametersUniform, mtl.ns, mtl.d)
            GLES20.glUniform3f(materialAmbientLightingUniform, mtl.ka.x, mtl.ka.y, mtl.ka.z)
            GLES20.glUniform3f(materialDiffuseLightingUniform, mtl.kd.x, mtl.kd.y, mtl.kd.z)
            GLES20.glUniform3f(materialSpecularLightingUniform, mtl.ks.x, mtl.ks.y, mtl.ks.z)
        }

        // Set the ModelViewProjection matrix in the shader
        GLES20.glUniformMatrix4fv(modelViewUniform, 1, false, modelViewMatrix, 0)
        GLES20.glUniformMatrix4fv(modelViewProjectionUniform, 1, false, modelViewProjectionMatrix, 0)
    }

    fun postDraw() {
        mtl.mapKd?.run {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        }
    }

    fun destroy() {
        if (owningThreadId != Thread.currentThread().id) {
            Timber.w("Calling destroy() from non-owning thread")
            return
        }

        GLES20.glDeleteProgram(programHandle)
        textureOptionMap.values
            .takeIf { it.isNotEmpty() }
            ?.toIntArray()
            ?.let {
                GLES20.glDeleteTextures(it.size, it, 0)
            }

        owningThreadId = null
    }

    private fun createTexture(opt: TextureOptions, handle: Int) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle)
        val bitmap = Glide.with(context)
            .asBitmap()
            .load(opt.fileName)
            .submit()
            .get()
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
            GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
            GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun loadTexture(opt: TextureOptions) {
        val textureHandle = textureOptionMap[opt]
                ?: throw RuntimeException("Failed to find texture handle for $opt")
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
        GLES20.glUniform1i(textureUniform, 0)
        GLES20.glUniform1i(textureValidUniform, 1)

        // Fake the object material properties
        GLES20.glUniform2f(materialParametersUniform, mtl.ns, mtl.d)
        GLES20.glUniform3f(materialAmbientLightingUniform, .5f, .5f, .5f)
        GLES20.glUniform3f(materialDiffuseLightingUniform, .5f, .5f, .5f)
        GLES20.glUniform3f(materialSpecularLightingUniform, mtl.ks.x, mtl.ks.y, mtl.ks.z)
    }
}