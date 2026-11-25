package io.github.machtvapps.machtv

import androidx.compose.animation.core.CubicBezierEasing

const val FOCUS_MS = 180
const val MESSAGE_MS = 4_000L

val OutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)
val FastOutSlowIn = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
