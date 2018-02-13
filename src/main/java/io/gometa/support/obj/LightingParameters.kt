// Copyright (c) 2018 GoMeta. All right reserved.

package io.gometa.support.obj

import android.support.annotation.FloatRange

/**
 *
 */
data class LightingParameters(
    @FloatRange(from = 0.0, to = 1.0) val lightIntensity: Float,
    val lightDirection: Vec4
) {
}