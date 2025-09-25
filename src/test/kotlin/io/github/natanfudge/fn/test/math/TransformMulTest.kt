package io.github.natanfudge.fn.test.math

import io.github.natanfudge.fn.render.Transform
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.sqrt

class TransformMulTest {

    private val EPS = 1e-5f

    private fun assertVecApprox(expected: Vec3f, actual: Vec3f, eps: Float = EPS) {
        assertEquals(expected.x, actual.x, eps, "x")
        assertEquals(expected.y, actual.y, eps, "y")
        assertEquals(expected.z, actual.z, eps, "z")
    }

    // q and -q represent the same rotation; account for that in comparisons
    private fun assertQuatApprox(expected: Quatf, actualRaw: Quatf, eps: Float = EPS) {
        var actual = actualRaw
        // Normalize both (just in case)
        fun Quatf.norm(): Quatf {
            val n = sqrt(w*w + x*x + y*y + z*z)
            return Quatf(x / n, y / n, z / n, w / n)
        }
        val e = expected.norm()
        actual = actual.norm()

        // Flip sign if needed so the dot is non-negative
        val dot = e.w*actual.w + e.x*actual.x + e.y*actual.y + e.z*actual.z
        val a = if (dot < 0f) Quatf(-actual.x, -actual.y, -actual.z, -actual.w) else actual

        assertEquals(e.w, a.w, eps, "w")
        assertEquals(e.x, a.x, eps, "x")
        assertEquals(e.y, a.y, eps, "y")
        assertEquals(e.z, a.z, eps, "z")
    }

    //TODO: very nice test, need more tests for this if we want to keep Transform.mul.
    @Test
    fun `parent z-scale with child Rx(90) matches expected TRS`() {
        // Parent (global)
        val parent = Transform(
            translation = Vec3f(0f, 0.5f, 103f),
            rotation    = Quatf.identity(),                 // (0,0,0,1)
            scale       = Vec3f(0.1f, 0.1f, 0.5f)
        )

        // Child (local)
        val childLocal = Transform(
            translation = Vec3f(0f, 0f, 1f),
            rotation    = Quatf(0.7f, 0f, 0f, 0.7f),        // approx Rx(90°); test will normalize
            scale       = Vec3f(1f, 1f, 1f)
        )

        val out = Transform()
        val world = parent.mul(childLocal, out)

        // Expected (derived analytically):
        // T: (0, 0.5, 103) + scale_parent * (0,0,1) = (0, 0.5, 103.5)
        // R: Rx(90°)  (same as child’s rotation)
        // S: parent scale applied in parent axes, seen in child’s local frame => (0.1, 0.5, 0.1)
        val expectedT = Vec3f(0f, 0.5f, 103.5f)
        val expectedR = Quatf(0.70710677f, 0f, 0f, 0.70710677f) // canonical Rx(90°)
        val expectedS = Vec3f(0.1f, 0.5f, 0.1f)

        assertVecApprox(expectedT, world.translation)
        assertQuatApprox(expectedR, world.rotation)
        assertVecApprox(expectedS, world.scale)

        // Ensure inputs unchanged
        assertVecApprox(Vec3f(0f, 0.5f, 103f), parent.translation)
        assertQuatApprox(Quatf.identity(), parent.rotation)
        assertVecApprox(Vec3f(0.1f, 0.1f, 0.5f), parent.scale)

        assertVecApprox(Vec3f(0f, 0f, 1f), childLocal.translation)
        assertQuatApprox(Quatf(0.7f, 0f, 0f, 0.7f), childLocal.rotation)
        assertVecApprox(Vec3f(1f, 1f, 1f), childLocal.scale)

        // dst must be returned and be the same instance we passed (common pattern)
        assertTrue(world === out, "mul should write into and return dst")
    }

    @Test
    fun `composition is not commutative`() {
        val parent = Transform(
            translation = Vec3f(0f, 0.5f, 103f),
            rotation    = Quatf.identity(),
            scale       = Vec3f(0.1f, 0.1f, 0.5f)
        )
        val childLocal = Transform(
            translation = Vec3f(0f, 0f, 1f),
            rotation    = Quatf(0.70710677f, 0f, 0f, 0.70710677f),
            scale       = Vec3f(1f, 1f, 1f)
        )

        val aThenB = parent.mul(childLocal, Transform())
        val bThenA = childLocal.mul(parent, Transform())

        // Quick inequality check on at least one component (translation Z differs)
        assertNotEquals(aThenB.translation.z, bThenA.translation.z, "parent*child should differ from child*parent")
    }

    @Test
    fun `dst must be distinct object (aliasing guard)`() {
        val a = Transform(
            translation = Vec3f(1f, 2f, 3f),
            rotation    = Quatf.identity(),
            scale       = Vec3f(2f, 2f, 2f)
        )
        val b = Transform(
            translation = Vec3f(4f, 5f, 6f),
            rotation = Quatf.identity(),
            scale = Vec3f(1f, 1f, 1f)
        )

        // If your mul() relies on dst being distinct, this should at least not corrupt the inputs.
        // We perform the correct call with a separate dst and verify inputs untouched.
        val aCopyBefore = a.copy()
        val bCopyBefore = b.copy()
        a.mul(b, Transform())

        // Inputs remain unchanged
        assertVecApprox(aCopyBefore.translation, a.translation)
        assertQuatApprox(aCopyBefore.rotation, a.rotation)
        assertVecApprox(aCopyBefore.scale, a.scale)

        assertVecApprox(bCopyBefore.translation, b.translation)
        assertQuatApprox(bCopyBefore.rotation, b.rotation)
        assertVecApprox(bCopyBefore.scale, b.scale)
    }
}
