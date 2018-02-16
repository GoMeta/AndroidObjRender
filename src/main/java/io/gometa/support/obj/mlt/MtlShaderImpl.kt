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

package io.gometa.support.obj.mlt

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import de.javagl.obj.Mtl
import io.gometa.support.obj.LightingParameters
import io.gometa.support.obj.MtlShader
import io.gometa.support.obj.R
import io.gometa.support.obj.VirtualObject
import timber.log.Timber

/**
 *
 */
class MtlShaderImpl(
    context: Context,
    mtl: Mtl
) : MtlShader(context, mtl) {

    override val fragmentShaderResId: Int
        get() = R.raw.frag_illum2

    var useVaryingNormal = true

    private var diffuseTextureHandle = 0
    private var ambientTextureHandle = 0
    private var specularTextureHandle = 0

    private var u_UseVaryingNormal = 0
    private var u_UseTexAmbient = 0
    private var u_UseTexDiffuse = 0
    private var u_UseTexSpecular = 0

    private var u_TexAmbient = 0
    private var u_TexDiffuse = 0
    private var u_TexSpecular = 0

    private var u_MtlAmbient = 0
    private var u_MtlDiffuse = 0
    private var u_MtlSpecular = 0

    private var u_D = 0
    private var u_SpecularPower = 0

    private var u_LightingParameters = 0

    private val viewLightDirection = FloatArray(4)

    override fun allocateTextures() {
        diffuseTextureHandle = mtl.mapKd?.let { allocateTexture(it, GLES20.GL_TEXTURE0) } ?: 0
        ambientTextureHandle = mtl.mapKa?.let { allocateTexture(it, GLES20.GL_TEXTURE1) } ?: 0
        specularTextureHandle = mtl.mapKs?.let { allocateTexture(it, GLES20.GL_TEXTURE2) } ?: 0
    }

    override fun onProgramCreated() {
        u_UseVaryingNormal = GLES20.glGetUniformLocation(programHandle, "u_UseVaryingNormal")
        u_UseTexAmbient = GLES20.glGetUniformLocation(programHandle, "u_UseTexAmbient")
        u_UseTexDiffuse = GLES20.glGetUniformLocation(programHandle, "u_UseTexDiffuse")
        u_UseTexSpecular = GLES20.glGetUniformLocation(programHandle, "u_UseTexSpecular")
        u_TexAmbient = GLES20.glGetUniformLocation(programHandle, "u_TexAmbient")
        u_TexDiffuse = GLES20.glGetUniformLocation(programHandle, "u_TexDiffuse")
        u_TexSpecular = GLES20.glGetUniformLocation(programHandle, "u_TexSpecular")
        u_MtlAmbient = GLES20.glGetUniformLocation(programHandle, "u_MtlAmbient")
        u_MtlDiffuse = GLES20.glGetUniformLocation(programHandle, "u_MtlDiffuse")
        u_MtlSpecular = GLES20.glGetUniformLocation(programHandle, "u_MtlSpecular")
        u_D = GLES20.glGetUniformLocation(programHandle, "u_D")
        u_SpecularPower = GLES20.glGetUniformLocation(programHandle, "u_SpecularPower")
        u_LightingParameters = GLES20.glGetUniformLocation(programHandle, "u_LightingParameters")
    }

    override fun preDraw(
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        lightingParameters: LightingParameters
    ) {
        super.preDraw(viewMatrix, projectionMatrix, lightingParameters)

        // Set the lighting environment properties.
        Matrix.multiplyMV(viewLightDirection, 0, modelViewMatrix, 0,
            lightingParameters.lightDirection.array, 0)
        VirtualObject.normalizeVec3(viewLightDirection)
        GLES20.glUniform4f(u_LightingParameters, viewLightDirection[0], viewLightDirection[1],
            viewLightDirection[2], lightingParameters.lightIntensity)

        GLES20.glUniform1f(u_D, mtl.d)
        GLES20.glUniform1f(u_SpecularPower, mtl.ns)
        GLES20.glUniform1i(u_UseVaryingNormal, if (useVaryingNormal) 1 else 0)
        bindTextureForDraw(mtl.mapKd, GLES20.GL_TEXTURE0, u_TexDiffuse, 0,
            diffuseTextureHandle, u_UseTexDiffuse, u_MtlDiffuse, mtl.kd)
        bindTextureForDraw(mtl.mapKa, GLES20.GL_TEXTURE1, u_TexAmbient, 1,
            ambientTextureHandle, u_UseTexAmbient, u_MtlAmbient, mtl.ka)
        bindTextureForDraw(mtl.mapKs, GLES20.GL_TEXTURE2, u_TexSpecular, 2,
            specularTextureHandle, u_UseTexSpecular, u_MtlSpecular, mtl.ks)
    }
}