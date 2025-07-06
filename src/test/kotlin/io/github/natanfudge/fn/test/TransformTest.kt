package io.github.natanfudge.fn.test

import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class TransformTest {

    @Test
    fun testTransformBasicOperations() {
        println("[DEBUG_LOG] Testing basic transform operations")

        // Test initial transform
        val transform = Transform()
        println("[DEBUG_LOG] Initial transform: translation=${transform.translation}, rotation=${transform.rotation}, scale=${transform.scale}")

        assertEquals(Vec3f.zero(), transform.translation, "Initial translation should be zero")
        assertEquals(Vec3f(1f, 1f, 1f), transform.scale, "Initial scale should be (1,1,1)")
        assertEquals(Quatf.identity(), transform.rotation, "Initial rotation should be identity")

        // Test creating transform with values
        val customTransform = Transform(
            translation = Vec3f(1f, 2f, 3f),
            rotation = Quatf.identity(),
            scale = Vec3f(2f, 2f, 2f)
        )

        assertEquals(Vec3f(1f, 2f, 3f), customTransform.translation, "Custom translation should be set")
        assertEquals(Vec3f(2f, 2f, 2f), customTransform.scale, "Custom scale should be set")

        // Test that transform is not compressed to zero
        assertNotEquals(Vec3f.zero(), customTransform.translation, "Transform should not be compressed to zero")
        assertNotEquals(Vec3f.zero(), customTransform.scale, "Scale should not be compressed to zero")
    }

    @Test
    fun testTransformCopy() {
        println("[DEBUG_LOG] Testing transform copy operations")

        val original = Transform(
            translation = Vec3f(1f, 2f, 3f),
            rotation = Quatf.identity(),
            scale = Vec3f(2f, 2f, 2f)
        )

        // Test copying with new translation
        val withNewTranslation = original.copy(translation = Vec3f(5f, 6f, 7f))

        assertEquals(Vec3f(5f, 6f, 7f), withNewTranslation.translation, "New translation should be set")
        assertEquals(Vec3f(2f, 2f, 2f), withNewTranslation.scale, "Scale should remain unchanged")
        assertEquals(Quatf.identity(), withNewTranslation.rotation, "Rotation should remain unchanged")

        // Original should be unchanged
        assertEquals(Vec3f(1f, 2f, 3f), original.translation, "Original translation should be unchanged")
    }

    @Test
    fun testTransformMatrix() {
        println("[DEBUG_LOG] Testing transform matrix conversion")

        val transform = Transform(
            translation = Vec3f(1f, 2f, 3f),
            rotation = Quatf.identity(),
            scale = Vec3f(1f, 1f, 1f)
        )

        val matrix = transform.toMatrix()
        println("[DEBUG_LOG] Transform matrix: $matrix")

        // The matrix should not be zero/identity when there's translation
        assertNotEquals(0f, matrix.m03, "Matrix should have translation component")
        assertNotEquals(0f, matrix.m13, "Matrix should have translation component")
        assertNotEquals(0f, matrix.m23, "Matrix should have translation component")
    }
}
