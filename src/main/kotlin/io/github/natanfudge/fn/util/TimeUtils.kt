package io.github.natanfudge.fn.util

import kotlin.time.Duration
import kotlin.time.DurationUnit

fun Duration.toFloat(unit: DurationUnit) = toDouble(unit).toFloat()