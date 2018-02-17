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

/**
 *
 */
open class Vec2() {
    constructor(x: Float, y: Float) : this() {
        this.x = x
        this.y = y
    }

    var x: Float
        get() = array[0]
        set(value) { array[0] = value }

    var y: Float
        get() = array[1]
        set(value) { array[1] = value }

    open val array = FloatArray(2)

    fun set(vararg values: Float) {
        values.take(array.size)
            .forEachIndexed { index, fl ->
                array[index] = fl
            }
    }

    fun normalize() {
        val length = array.sumByDouble { (it * it).toDouble() }.toFloat()
        (0 until array.size).forEach {
            array[it] /= length
        }
    }

    fun invert() {
        (0 until array.size).forEach {
            array[it] = -array[it]
        }
    }

    override fun toString(): String {
        return array.joinToString(prefix = "Vec2(", postfix = ")")
    }
}

/**
 *
 */
open class Vec3() : Vec2() {
    constructor(x: Float, y: Float, z: Float) : this() {
        this.x = x
        this.y = y
        this.z = z
    }

    var z: Float
        get() = array[2]
        set(value) { array[2] = value }

    override val array = FloatArray(3)

    override fun toString(): String {
        return array.joinToString(prefix = "Vec3(", postfix = ")")
    }
}

/**
 *
 */
open class Vec4() : Vec3() {
    constructor(x: Float, y: Float, z: Float, w: Float) : this() {
        this.x = x
        this.y = y
        this.z = z
        this.w = w
    }

    var w: Float
        get() = array[3]
        set(value) { array[3] = value }

    override val array = FloatArray(4)

    override fun toString(): String {
        return array.joinToString(prefix = "Vec4(", postfix = ")")
    }
}