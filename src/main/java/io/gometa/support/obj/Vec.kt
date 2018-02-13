// Copyright (c) 2018 GoMeta. All right reserved.

package io.gometa.support.obj

/**
 *
 */
sealed class Vec2() {
    constructor(x: Float, y: Float) : this() {
        this.x = x
        this.y = y
    }
    var x: Float
        get() = array[0]
        set(value) { array[1] = value }
    var y: Float
        get() = array[1]
        set(value) { array[1] = value }
    open val array = FloatArray(2)
}

/**
 *
 */
sealed class Vec3() : Vec2() {
    constructor(x: Float, y: Float, z: Float) : this() {
        this.x = x
        this.y = y
        this.z = z
    }
    var z: Float
        get() = array[2]
        set(value) { array[2] = value }
    override val array = FloatArray(3)
}

/**
 *
 */
class Vec4() : Vec3() {
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
}