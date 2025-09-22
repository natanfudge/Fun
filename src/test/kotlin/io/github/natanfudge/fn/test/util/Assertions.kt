package io.github.natanfudge.fn.test.util

import io.github.natanfudge.fn.core.SimpleLogger
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.abs

/**
 * Asserts that this [Vec3f] is roughly equal to the [other] vector.
 *
 * The comparison is done component-wise. The assertion fails if the absolute
 * difference of any corresponding component (x, y, or z) is greater than
 * the specified [epsilon].
 *
 * @param other The [Vec3f] to compare against.
 * @param epsilon The maximum allowed absolute difference for each component.
 * @throws AssertionError if the vectors are not roughly equal.
 */
fun Vec3f.shouldRoughlyEqual(other: Vec3f, name: String, throwOnFailure: Boolean = true, epsilon: Float = 1e-5f) {
    // Assert that each component is roughly equal.
    // This reuses our previously defined number assertion.
    val xEq = this.x.roughlyEquals(other.x, epsilon.toDouble())
    val yEq = this.y.roughlyEquals(other.y, epsilon.toDouble())
    val zEq = this.z.roughlyEquals(other.z, epsilon.toDouble())
    if ((!xEq || !yEq || !zEq)) {
        val message = "Assertion failed: unexpected $name\n" +
                "Expected: <$other>\n" +
                "Actual:   <$this>\n" +
                "Epsilon:  $epsilon\n"
        if (throwOnFailure) {
            throw AssertionError(message)
        } else {
            SimpleLogger.error("AssertionError"){message}
        }
    }
}

/**
 * Asserts that the receiver [Number] is roughly equal to the [other] number
 * within a specified tolerance [epsilon].
 *
 * This is useful for comparing floating-point numbers (Float, Double) where
 * exact equality checks can be unreliable due to precision issues. It also works
 * for integer types.
 *
 * @param other The number to compare against. It must be of the same type `T`.
 * @param epsilon The maximum allowed absolute difference between the two numbers.
 */
fun <T : Number> T.roughlyEquals(other: T, epsilon: Double = 1e-5): Boolean {
    // Convert both numbers to Double for a consistent comparison, as epsilon is a Double.
    val thisAsDouble = this.toDouble()
    val otherAsDouble = other.toDouble()

    // Calculate the absolute difference.
    val difference = abs(thisAsDouble - otherAsDouble)

    return difference <= epsilon
}

/**
 * An overload for `Double` to avoid unnecessary type conversion and provide
 * a more direct implementation.
 */
fun Double.shouldRoughlyEqual(other: Double, epsilon: Double = 1e-5) {
    val difference = abs(this - other)
    if (difference > epsilon) {
        val message = "Assertion failed: Expected a value roughly equal to <$other> but was <$this>. " +
                "The difference is $difference, which is greater than the allowed epsilon of $epsilon."
        throw AssertionError(message)
    }
}