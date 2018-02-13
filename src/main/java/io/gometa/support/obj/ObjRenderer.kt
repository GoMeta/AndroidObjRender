// Copyright (c) 2018 GoMeta. All right reserved.

package io.gometa.support.obj

import android.opengl.GLES20
import de.javagl.obj.Obj
import de.javagl.obj.ObjData

/**
 *
 */
class ObjRenderer(
    private val obj: Obj,
    private val mtl: MtlRenderer
) : VirtualObject {

    private val indices = ObjData.convertToShortBuffer(ObjData.getFaceVertexIndices(obj))
    private val vertices = ObjData.getVertices(obj)
    private val texCoords = ObjData.getTexCoords(obj, 2)
    private val normals = ObjData.getNormals(obj)
    private var indexCount = 0

    private val _buffers = IntArray(2)
    private val vertexBufferId
        get() = _buffers[0]
    private val indexBufferId
        get() = _buffers[1]

    private var verticesBaseAddress = 0
    private var texCoordsBaseAddress = 0
    private var normalsBaseAddress = 0

    private var owningThreadId: Long? = null

    override fun createOnGlThread() {
        if (owningThreadId == Thread.currentThread().id) {
            // Already initialized for this GL context
            return
        } else if (owningThreadId != null) {
            throw RuntimeException("This renderer is owned by another GL context")
        }
        // Make sure the material was created
        mtl.createOnGlThread()

        GLES20.glGenBuffers(_buffers.size, _buffers, 0)

        // Load vertex buffer
        verticesBaseAddress = 0
        texCoordsBaseAddress = verticesBaseAddress + 4 * vertices.limit()
        normalsBaseAddress = texCoordsBaseAddress + 4 * texCoords.limit()
        val totalBytes = normalsBaseAddress + 4 * normals.limit()

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, totalBytes, null, GLES20.GL_STATIC_DRAW)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, verticesBaseAddress,
            4 * vertices.limit(), vertices)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, texCoordsBaseAddress,
            4 * texCoords.limit(), texCoords)
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, normalsBaseAddress,
            4 * normals.limit(), normals)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Load index buffer
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        indexCount = indices.limit()
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, 2 * indexCount, indices,
            GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        owningThreadId = Thread.currentThread().id
    }

    override fun updateModelMatrix(anchorMatrix: FloatArray, scaleFactor: Float) {
        mtl.updateModelMatrix(anchorMatrix, scaleFactor)
    }

    override fun draw(viewMatrix: FloatArray, projectionMatrix: FloatArray,
        lightingParameters: LightingParameters) {
        mtl.draw(viewMatrix, projectionMatrix, lightingParameters)

        // Set the vertex attributes
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vertexBufferId)
        GLES20.glVertexAttribPointer(mtl.positionAttribute, 3, GLES20.GL_FLOAT, false,
            0, verticesBaseAddress)
        GLES20.glVertexAttribPointer(mtl.normalAttribute, 3, GLES20.GL_FLOAT, false,
            0, normalsBaseAddress)
        GLES20.glVertexAttribPointer(mtl.texCoordAttribute, 2, GLES20.GL_FLOAT, false,
            0, texCoordsBaseAddress)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(mtl.positionAttribute)
        GLES20.glEnableVertexAttribArray(mtl.normalAttribute)
        GLES20.glEnableVertexAttribArray(mtl.texCoordAttribute)

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, indexBufferId)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, indexCount, GLES20.GL_UNSIGNED_SHORT, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)

        // Disable vertex arrays
        GLES20.glDisableVertexAttribArray(mtl.positionAttribute)
        GLES20.glDisableVertexAttribArray(mtl.normalAttribute)
        GLES20.glDisableVertexAttribArray(mtl.texCoordAttribute)

        mtl.postDraw()
    }

    override fun destroy() {
        // Do not destroy the MtlRenderer, others might be using it.
        if (owningThreadId != Thread.currentThread().id) {
            throw RuntimeException("Calling destroy() from non-owning thread")
        }

        GLES20.glDeleteBuffers(_buffers.size, _buffers, 0)

        owningThreadId = null
    }
}