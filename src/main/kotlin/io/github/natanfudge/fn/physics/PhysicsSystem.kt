package io.github.natanfudge.fn.physics

import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit


// SLOW: now that we have commit we can easily avoid allocations now I think
/**
 * The physics system measures position in meters, velocity in meters per second, and acceleration in meters per second squared.
 * It is assumed the Z axis is down (sorry Minecraft bros).
 */
class PhysicsSystem(var gravity: Boolean = true) {
    var earthGravityAcceleration = 9.8f

    companion object {
        private val maxDelta = 40.milliseconds
        private val maxSkipTicks = 50
    }


    private val bodies = mutableListOf<Body>()

    fun add(obj: Body) {
        bodies.add(obj)
    }

    fun remove(obj: Body) {
        bodies.remove(obj)
    }

//    fun getTouching(body: Body): List<Body> {
//        val intersections = bodies.filter { intersect(body, it) && it !== body }
//        return intersections
//    }

    private fun isGrounded(body: Body): Boolean {
        for (other in bodies) {
            if (other.isImmovable) {
                val zOverlap = other.boundingBox.maxZ - body.boundingBox.minZ
                val xOverlap = other.boundingBox.overlap(body.boundingBox, 0)
                val yOverlap = other.boundingBox.overlap(body.boundingBox, 1)

                // Check that the other is *below* the body and overlaps in X & Y
                if (zOverlap >= -0.00001 && other.boundingBox.minZ <= body.boundingBox.maxZ && xOverlap > 0 && yOverlap > 0) {
                    return true
                }
            }
        }
        return false
    }

    private fun intersect(bodyA: Body, bodyB: Body) = bodyA.boundingBox.intersects(bodyB.boundingBox)

    fun tick(delta: Duration) {
        if (delta > maxDelta) {
            val ticks = ceil(delta / maxDelta).toInt()
            if (ticks > maxSkipTicks) {
                println("Warn: physics is lagging behind by more than $maxSkipTicks ticks ($maxSkipTicks)!  Only $maxSkipTicks ticks will be emulated.")
                repeat(maxSkipTicks) {
                    singleTick(maxDelta)
                }
            } else {
                println("Warn: physics is lagging behind, quickly emulating $ticks ticks. If the game is sped up you can ignore this.")
                repeat(ticks - 1) {
                    singleTick(maxDelta)
                }
                // Run the leftover delta
                singleTick(delta - (maxDelta * (ticks - 1)))
            }

        } else {
            singleTick(delta)
        }
    }


    /**
     * Delta should be the fraction of the unit, so in this case in fractions of a second
     */
    private fun singleTick(delta: Duration) {
        val deltaSeconds = delta.seconds
        for (body in bodies) {
            if (!body.isImmovable) {
                if (gravity) applyGravity(body, deltaSeconds)
                applyDisplacement(body, deltaSeconds)
                if (!body.isImmovable) {
//                    println("After displacement, Velocity = ${body.velocity.toString(round = false)}, pos = ${body.position}")
                }
            }
        }
        val intersections = getIntersections()
//        var pushedOut = false
        for ((a, b) in intersections) {
            // When a body encounters an immovable object, we "push out" the body from the immovable object.
            // We make sure to do this first, then we resolve whether we need to stop velocity as a result of hitting a floor/wall
            if (a.isImmovable) {
                if (!b.isImmovable) {
                    pushOut(surface = a, body = b)
//                    pushedOut = true
//                    println("After pushOut,, pos = ${b.position}")
                }

            } else if (b.isImmovable) {
                if (!a.isImmovable) {
                    pushOut(surface = b, body = a)
//                    pushedOut = true
//                    println("After pushOut, pos = ${a.position}")
                }
            } else {
                applyElasticCollision(a, b)
            }
        }

        // Now that nothing is inside the wall/floor, we can check if we hit a wall or stand on a floor
        for ((a, b) in intersections) {
            // Floor has special handling, we simply place the elements above the ground
            if (a.isImmovable) {
                if (!b.isImmovable) {
                    stop(surface = a, body = b)
                    if (!b.isImmovable) {
//                        println("After stop, Velocity = ${b.velocity.toString(round = false)}, pos = ${b.position}")
                    }
                }

            } else if (b.isImmovable) {
                if (!a.isImmovable) {
                    stop(surface = b, body = a)
                    if (!a.isImmovable) {
//                        println("After stop, Velocity = ${a.velocity.toString(round = false)}, pos = ${a.position}")
                    }
                }
            }
        }
        bodies.forEach {
            if (!it.isImmovable) {
                // Don't care about isGrounded for immovable stuff.
                it.isGrounded = isGrounded(it)
            }
            it.commit()
        }
    }

    /**
     * If a body touches the floor / wall, we firmly place it above the floor / wall and stop it from moving
     */
    private fun pushOut(surface: Body, body: Body) {
        val overlapByAxis = (0..2).map {
            it to surface.boundingBox.overlap(body.boundingBox, it)
        }

        // Push the body apart along the axis with the smallest overlap, so it stops and doesn't sink into the surface.
        val pushoutAxis = overlapByAxis.minBy { it.second }.first

        if (body.boundingBox.min(pushoutAxis) >= surface.boundingBox.min(pushoutAxis)) {
//            println("Min push")
            val sinkAmount = surface.boundingBox.max(pushoutAxis) - body.boundingBox.min(pushoutAxis)
//            println("Sinkamount = $sinkAmount, to go from ${body.boundingBox.min(pushoutAxis)} to ${body.boundingBox.min(pushoutAxis) + sinkAmount}")

            // Place above surface
            body.position = body.position.copy(
                // Round a bit to make it stable
                pushoutAxis, value = (body.position[pushoutAxis] + sinkAmount).roundUpTo5DecimalPoints()
            )
//            println("New min: ${body.boundingBox.min(pushoutAxis)}")

        } else {
            val sinkAmount = (body.boundingBox.max(pushoutAxis) - surface.boundingBox.min(pushoutAxis))
            // Place below surface
            body.position = body.position.copy(
                // Round a bit to make it stable
                pushoutAxis, value = (body.position[pushoutAxis] - sinkAmount).roundDownTo5DecimalPoints()
            )
        }
    }

    /**
     * If a body is close to a surface, and will reasonably touch it, we do not allow it to move through the surface, in the axis of that surface.
     */
    private fun stop(surface: Body, body: Body) {
        val overlapByAxis = (0..2).map {
            it to surface.boundingBox.overlap(body.boundingBox, it)
        }

        // Push the body apart along the axis with the smallest overlap, so it stops.
        val pushoutAxis = overlapByAxis.minBy { it.second }.first

        val otherAxes = overlapByAxis.filter { it.first != pushoutAxis }
        // Only stop if the body is reasonably touching the surface in the OTHER axes
        if (otherAxes.all { it.second > 0.001f }) {
            stopInAxis(body, pushoutAxis)
        }
    }

    private fun stopInAxis(body: Body, axis: Int) {
        body.velocity = body.velocity.copy(axis = axis, value = 0f)
        body.acceleration = body.acceleration.copy(axis = axis, value = 0f)
    }

    private fun applyElasticCollision(a: Body, b: Body) {
        val direction = b.position - a.position
        val n = direction.normalize()
        val delta = (a.velocity - b.velocity).dot(n)
        val newV1 = a.velocity - n * ((2 * b.mass) / (a.mass + b.mass)) * delta
        val newV2 = b.velocity + n * ((2 * a.mass) / (a.mass + b.mass)) * delta
        a.velocity = newV1
        b.velocity = newV2
    }

    private fun applyDisplacement(body: Body, deltaSeconds: Double) {
        body.velocity = applyChange(deltaSeconds, body.velocity, body.acceleration)
        body.position = applyChange(deltaSeconds, body.position, body.velocity)
        body.rotation = updateRotation(body.rotation, body.angularVelocity, deltaSeconds.toFloat())
    }

    /**
     * Applies changes in value like velocity or acceleration in a way that works better with small floating point numbers.
     */
    private fun applyChange(delta: Double, prevValue: Vec3f, change: Vec3f): Vec3f {
        if (change.isZero) return prevValue
        val xIncrement = if (change.x == 0f) 0f else {
            // If the change is non-zero we want the new value to actually change. If we don't do this, extremely small values might not cause any change in
            // the final value, so we change by at least some small value
            val changeAbs = max(abs(change.x * delta).toFloat(), 0.00001f)
            if (change.x > 0) changeAbs else -changeAbs
        }

        val yIncrement = if (change.y == 0f) 0f else {
            val changeAbs = max(abs(change.y * delta).toFloat(), 0.00001f)
            if (change.y > 0) changeAbs else -changeAbs
        }

        val zIncrement = if (change.z == 0f) 0f else {
            val changeAbs = max(abs(change.z * delta).toFloat(), 0.00001f)
            if (change.z > 0) changeAbs else -changeAbs
        }

        return Vec3f(prevValue.x + xIncrement, prevValue.y + yIncrement, prevValue.z + zIncrement)
    }

    private fun applyGravity(body: Body, deltaSeconds: Double) {
        if (body.affectedByGravity) {
            body.velocity = body.velocity.copy(
                z = body.velocity.z - earthGravityAcceleration * deltaSeconds.toFloat()
            )
        }
    }

    private fun getIntersections(): List<Pair<Body, Body>> {
        // O(n) partition
        val movables = ArrayList<Body>()
        // We expect there to be much more immovables
        val immovables = ArrayList<Body>(bodies.size)
        for (element in bodies) {
            if (element.isImmovable) {
                immovables.add(element)
            } else {
                movables.add(element)
            }
        }

        val hits = ArrayList<Pair<Body, Body>>()

        /* ── movable ↔ movable ───────────────────────────────────────────── */
        for (i in movables.indices) {
            val a = movables[i]
            for (j in i + 1 until movables.size) {   // j > i ⇒ no duplicate order
                val b = movables[j]
                if (intersect(a, b)) hits += a to b
            }
        }

        /* ── movable ↔ immovable ─────────────────────────────────────────── */
        for (a in movables) {
            // order chosen once → no duplicates
            for (b in immovables) {
                if (intersect(a, b)) hits += a to b
            }
        }
        return hits
    }

    /**
     * Updates a rotation quaternion based on an angular velocity vector.
     * Mutates [currentRotation]
     */
    private fun updateRotation(currentRotation: Quatf, angularVelocity: Vec3f, deltaTime: Float): Quatf {
        // We'll work with a local copy
        var rotation = currentRotation

        // Calculate the magnitude of the angular velocity
        val angle = angularVelocity.length

        // A small epsilon to avoid issues with zero-length vectors
        if (angle > 0.0001) {
            // The total angle to rotate this frame is the angular speed (angle) * time
            // The quaternion formula uses half the angle.
            val halfAngle = angle * deltaTime * 0.5

            // The axis of rotation is the normalized angular velocity vector.
            // We can get it by dividing the vector by its length (angle).
            val axis = angularVelocity / angle

            // Create the delta rotation quaternion
            val sin_hA = sin(halfAngle).toFloat()
            val cos_hA = cos(halfAngle).toFloat()

            val deltaRotation = Quatf(
                w = cos_hA,
                x = axis.x * sin_hA,
                y = axis.y * sin_hA,
                z = axis.z * sin_hA
            )

            // Apply the delta rotation to the current rotation.
            // The order is important: delta * current applies rotation in world space.
            rotation = deltaRotation * rotation

            // Re-normalize the quaternion to prevent floating-point drift.
            // This is crucial for stability over time.
            rotation.normalize(currentRotation)
        }
        // If angularVelocity is zero, no change in rotation.
        return rotation
    }
}

fun Float.roundUpTo5DecimalPoints(): Float {
    val scale = 100_000f
    return ceil(this * scale) / scale
}

fun Float.roundDownTo5DecimalPoints(): Float {
    val scale = 100_000f
    return floor(this * scale) / scale
}


private operator fun Vec3f.times(ue: Double) = Vec3f((x * ue).toFloat(), (y * ue).toFloat(), (z * ue).toFloat())

private val Duration.seconds get() = toDouble(DurationUnit.SECONDS)