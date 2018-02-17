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

import de.javagl.obj.Rect3D

/**
 *
 */
class ObjRenderer(
    private val parts: List<VirtualObject>,
    private val mtls: Collection<MtlShader>? = null
) : VirtualObject {

    private val _bounds: Rect3D

    override val bounds: Rect3D
        get() = _bounds

    override var scaleFactor: Float = 1f

    override fun createOnGlThread() {
        parts.forEach { it.createOnGlThread() }
    }

    override fun updateModelMatrix(anchorMatrix: FloatArray, scaleFactor: Float) {
        parts.forEach { it.updateModelMatrix(anchorMatrix, this.scaleFactor * scaleFactor) }
    }

    override fun draw(
        viewMatrix: FloatArray,
        projectionMatrix: FloatArray,
        lightingParameters: LightingParameters
    ) {
        parts.forEach { it.draw(viewMatrix, projectionMatrix, lightingParameters) }
    }

    override fun destroy() {
        parts.forEach { it.destroy() }
        mtls?.forEach { it.destroy() }
    }

    init {
        var combinedBounds: Rect3D? = null
        parts.forEach {
            combinedBounds = combinedBounds?.add(it.bounds) ?: it.bounds
        }
        _bounds = combinedBounds ?: throw IllegalArgumentException("Parts must contain bounds")
    }
}