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
import android.os.Handler
import android.os.Looper
import de.javagl.obj.Mtl
import de.javagl.obj.MtlReader
import de.javagl.obj.Obj
import de.javagl.obj.ObjReader
import de.javagl.obj.ObjSplitting
import de.javagl.obj.ObjUtils
import de.javagl.obj.TextureOptions
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 *
 */
class ObjExtractor(
    private val context: Context,
    private val fileName: String,
    private val zipFile: InputStream,
    private val callback: OnExtractionListener,
    private val diskIoHandler: Handler? = null,
    private val computeHandler: Handler? = null
) {
    companion object {
        private val mainHandler = Handler(Looper.getMainLooper())
    }
    interface OnExtractionListener {
        fun onExtractionFinished(renderer: ObjRenderer?)
    }

    private val targetDir = File(context.cacheDir, fileName)

    private fun extractFiles() {
        if (targetDir.exists()) {
            readObj()
            return
        }
        var zipInputStream: ZipInputStream? = null
        try {
            targetDir.mkdirs()
            zipInputStream = ZipInputStream(zipFile)
            var entry: ZipEntry?
            val buffer = ByteArray(1024)
            var count: Int
            do {
                entry = zipInputStream.nextEntry ?: break
                val file = File(targetDir, entry.name)
                if (file.isDirectory) {
                    file.mkdirs()
                    continue
                }
                file.parentFile.mkdirs()

                val fout = FileOutputStream(file)
                do {
                    count = zipInputStream.read(buffer)
                    if (count > 0) {
                        fout.write(buffer, 0, count)
                    }
                } while (count != -1)
                fout.close()
                zipInputStream.closeEntry()
            } while (true)
            readObj()
        } catch (tr: Throwable) {
            Timber.e(tr, "Error extracting %s", zipFile)
            postResult(null)
        } finally {
            zipInputStream?.close()
        }
    }

    private fun readObj() {
        val objFile = targetDir.listFiles()
            .find { it.extension == "obj" }
        if (objFile == null) {
            Timber.e("Failed to find .obj file in %s", zipFile)
            postResult(null)
            return
        }
        val obj = ObjReader.read(FileInputStream(objFile))
        val mtlMap = HashMap<String, MtlRenderer>()
        obj.mtlFileNames
            .map { "${targetDir.absolutePath}${File.separator}$it" }
            .forEach {
                val mtlList = MtlReader.read(FileInputStream(it))
                // Fix the file paths
                mtlList.forEach(this::fixTextureMapFilePaths)
                // Add the mtls to the map
                mtlMap.putAll(mtlList.associate {
                    it.name to MtlRenderer(context, it)
                })
            }

        computeHandler?.post { createRenderers(mtlMap, obj) }
                ?: run { createRenderers(mtlMap, obj) }
    }

    private fun createRenderers(mtlMap: HashMap<String, MtlRenderer>, obj: Obj) {
        val renderers = ArrayList<SingleObjRenderer>()
        ObjUtils.convertToRenderable(obj)
            .let(ObjSplitting::splitByMaterialGroups)
            .entries
            .forEach { (materialName, obj) ->
                val mtlRenderer = mtlMap[materialName] ?: run {
                    Timber.e("Cannot find material '%s'", materialName)
                    mainHandler.post { callback.onExtractionFinished(null) }
                    return@forEach
                }
                if (obj.numVertices <= Short.MAX_VALUE) {
                    renderers.add(SingleObjRenderer(obj, mtlRenderer))
                } else {
                    ObjSplitting.splitByMaxNumVertices(obj, Short.MAX_VALUE.toInt())
                        .forEach { objPart ->
                            renderers.add(SingleObjRenderer(objPart, mtlRenderer))
                        }
                }
            }
        Timber.d("Creating renderer from %d parts and %d materials",
            renderers.size, mtlMap.size)
        postResult(ObjRenderer(renderers, mtlMap.values))
    }

    private fun fixTextureMapFilePaths(mtl: Mtl) {
        mtl.mapKa = mtl.mapKa?.let(this::fixTextureOptionsFilePath)
        mtl.mapKd = mtl.mapKd?.let(this::fixTextureOptionsFilePath)
        mtl.mapKs = mtl.mapKs?.let(this::fixTextureOptionsFilePath)
        mtl.mapNs = mtl.mapNs?.let(this::fixTextureOptionsFilePath)
        mtl.mapD = mtl.mapD?.let(this::fixTextureOptionsFilePath)
        mtl.bumpMap = mtl.bumpMap?.let(this::fixTextureOptionsFilePath)
        mtl.displacementMap = mtl.displacementMap?.let(this::fixTextureOptionsFilePath)
        mtl.decalMap = mtl.decalMap?.let(this::fixTextureOptionsFilePath)
    }

    private fun fixTextureOptionsFilePath(opts: TextureOptions): TextureOptions =
        TextureOptions.Builder(opts)
            .setFileName("$targetDir${File.separator}${opts.fileName}")
            .build()

    private fun postResult(result: ObjRenderer?) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            callback.onExtractionFinished(result)
        } else {
            mainHandler.post { callback.onExtractionFinished(result) }
        }
    }

    init {
        diskIoHandler?.post { extractFiles() }
                ?: run { extractFiles() }
    }
}