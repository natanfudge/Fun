package io.github.natanfudge.fn.test.util

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
fun Vec3f.shouldRoughlyEqual(other: Vec3f, epsilon: Double = 1e-5) {
    try {
        // Assert that each component is roughly equal.
        // This reuses our previously defined number assertion.
        this.x.shouldRoughlyEqual(other.x, epsilon)
        this.y.shouldRoughlyEqual(other.y, epsilon)
        this.z.shouldRoughlyEqual(other.z, epsilon)
    } catch (e: AssertionError) {
        // If any component assertion fails, catch the error and re-throw it
        // with a more descriptive, high-level message about the vectors.
        val detailedMessage = "Assertion failed: Vectors are not roughly equal.\n" +
                "Expected: <$other>\n" +
                "Actual:   <$this>\n" +
                "Epsilon:  $epsilon\n" +
                "Reason:   ${e.message}" // Include the specific component failure message.

        // Throw a new AssertionError with the combined message.
        throw AssertionError(detailedMessage)
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
 * @throws AssertionError if the absolute difference between the receiver and `other`
 *         is greater than [epsilon].
 *
 * @see Double.shouldRoughlyEqual
 */
fun <T : Number> T.shouldRoughlyEqual(other: T, epsilon: Double = 1e-5) {
    // Convert both numbers to Double for a consistent comparison, as epsilon is a Double.
    val thisAsDouble = this.toDouble()
    val otherAsDouble = other.toDouble()

    // Calculate the absolute difference.
    val difference = abs(thisAsDouble - otherAsDouble)

    // If the difference is outside the allowed tolerance, the assertion fails.
    if (difference > epsilon) {
        // Throw an AssertionError with a descriptive message to help with debugging.
        val message = "Assertion failed: Expected a value roughly equal to <$other> but was <$this>. " +
                "The difference is $difference, which is greater than the allowed epsilon of $epsilon."
        throw AssertionError(message)
    }

    // If the assertion passes, the function simply returns.
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