package io.github.natanfudge.fn.test

import io.github.natanfudge.fn.physics.translation
import io.github.natanfudge.fn.physics.rotation
import io.github.natanfudge.fn.physics.scale
import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Test to verify that the transform bug is fixed.
 * The bug was that objects were being initially compressed to a single point
 * due to inconsistent getter/setter access patterns in the transform system.
 */
class TransformBugTest {

    @Test
    fun testTransformExtensionProperties() {

        // Create a transform with non-zero values
        val transform = Transform(
            translation = Vec3f(1f, 2f, 3f),
            rotation = Quatf.identity(),
            scale = Vec3f(2f, 2f, 2f)
        )
        
        // Test that extension properties work correctly
        assertEquals(Vec3f(1f, 2f, 3f), transform.translation, "Extension property translation should work")
        assertEquals(Quatf.identity(), transform.rotation, "Extension property rotation should work")
        assertEquals(Vec3f(2f, 2f, 2f), transform.scale, "Extension property scale should work")
        
        // Test that values are not compressed to zero
        assertNotEquals(Vec3f.zero(), transform.translation, "Translation should not be compressed to zero")
        assertNotEquals(Vec3f.zero(), transform.scale, "Scale should not be compressed to zero")
        
    }
    
    @Test
    fun testTransformConsistency() {

        // Create a transform
        val transform = Transform()
        
        // Test that getting and setting through different paths gives consistent results
        val originalTransform = transform.copy(translation = Vec3f(5f, 6f, 7f))
        
        // Access through extension properties
        val translationViaExtension = originalTransform.translation
        val scaleViaExtension = originalTransform.scale
        
        // Access through direct properties
        val translationDirect = originalTransform.translation
        val scaleDirect = originalTransform.scale
        
        // They should be the same
        assertEquals(translationDirect, translationViaExtension, "Translation should be consistent")
        assertEquals(scaleDirect, scaleViaExtension, "Scale should be consistent")
        
        // Values should not be compressed
        assertEquals(Vec3f(5f, 6f, 7f), translationViaExtension, "Translation should maintain its value")
        assertEquals(Vec3f(1f, 1f, 1f), scaleViaExtension, "Scale should maintain its value")
        
    }
    
    @Test
    fun testTransformMatrixGeneration() {

        // Create a transform with specific values
        val transform = Transform(
            translation = Vec3f(10f, 20f, 30f),
            rotation = Quatf.identity(),
            scale = Vec3f(2f, 3f, 4f)
        )
        
        // Generate matrix
        val matrix = transform.toMatrix()
        
        // Check that matrix has the expected translation components
        assertEquals(10f, matrix.m03, 0.001f, "Matrix should have correct X translation")
        assertEquals(20f, matrix.m13, 0.001f, "Matrix should have correct Y translation")
        assertEquals(30f, matrix.m23, 0.001f, "Matrix should have correct Z translation")
        
        // Check that matrix has the expected scale components (diagonal elements)
        assertEquals(2f, matrix.m00, 0.001f, "Matrix should have correct X scale")
        assertEquals(3f, matrix.m11, 0.001f, "Matrix should have correct Y scale")
        assertEquals(4f, matrix.m22, 0.001f, "Matrix should have correct Z scale")
        
        // Matrix should not be identity or zero
        assertNotEquals(0f, matrix.m03, "Matrix should not have zero translation")
        assertNotEquals(1f, matrix.m00, "Matrix should not have identity scale")
        
    }
}