package io.github.natanfudge.fn.mte

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.unit.IntOffset
import io.github.natanfudge.fn.base.ModelAnimator
import io.github.natanfudge.fn.base.getHoveredParent
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.child
import io.github.natanfudge.fn.core.logPerformance
import io.github.natanfudge.fn.physics.Body
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.render.AxisAlignedBoundingBox
import io.github.natanfudge.fn.render.Model
import io.github.natanfudge.fn.render.render
import io.github.natanfudge.wgpu4k.matrix.Quatf
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import korlibs.math.squared
import korlibs.time.div
import korlibs.time.seconds
import java.lang.management.ManagementFactory
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds


private fun timeSinceStartup(): Duration {
    val mxBean = ManagementFactory.getRuntimeMXBean()

    // 2. Elapsed time since startup (updated on every call)
    val upMillis = mxBean.uptime // <-- most convenient

    return upMillis.milliseconds
}


class Player(private val game: DeepSoulsGame) : Fun("Player") {
    val model = Model.fromGlbResource("files/models/joe.glb")
    val physics = physics(game.physics.system)
    val render by render(model, physics)
    val pickaxe by render(
        Model.fromGlbResource("files/models/items/pickaxe.glb"), parent = render.joint("mixamorig:RightHand")
    )
    val animation = ModelAnimator(render)

    init {
        pickaxe.localTransform.translation = Vec3f(0.07f, 0.11f, 0.01f)
        pickaxe.localTransform.scale = Vec3f(0.5f, 5f, 0.5f)
        pickaxe.localTransform.rotation = Quatf.identity().rotateZ(-2.3f / 2)
    }

    val inventory = Inventory(game)

    private val mineDelay = DelayedStrike(this)

    private val left = game.input.registerHotkey("Left", Key.A)
    private val right = game.input.registerHotkey("Right", Key.D)
    private val jump = game.input.registerHotkey("Jump", Key.Spacebar)
    private val breakKey = game.input.registerHotkey("Break", PointerButton.Primary)

    private var wasInAirLastFrame = false
    private var printedStartupTime = false

    private val legBones = model.nodesAndTheirChildren("mixamorig:LeftUpLeg.R", "mixamorig:LeftUpLeg.L").toSet()
    private val upperBodyBones = model.nodesAndTheirChildren("mixamorig:Spine1").toSet()


    init {
        render.localTransform.translation = Vec3f(0f, 0f, -0.5f)
        physics.baseAABB = AxisAlignedBoundingBox(
            minX = -0.2f, maxX = 0.2f,
            minZ = -0.49f, maxZ = 0.5f,
            minY = -0.23f, maxY = 0.1f,
        )

        events.beforePhysics.listen {
            val deltaSecs = it.seconds.toFloat()
            val grounded = physics.isGrounded

            // Make devil request quota on first landing
            // see https://github.com/natanfudge/MineTheEarth/issues/122
            if (grounded && !game.devil.quotaRequested) game.devil.quotaRequested = true

            val isJumping = jump.isPressed && grounded

            if (left.isPressed) {
                physics.orientation = Quatf.identity().rotateZ(PI.toFloat() / -2)
                physics.position -= Vec3f(deltaSecs * 3, 0f, 0f)
            } else if (right.isPressed) {
                physics.orientation = Quatf.identity().rotateZ(PI.toFloat() / 2)
                physics.position += Vec3f(deltaSecs * 3, 0f, 0f)
            }
            if (isJumping) {
                physics.velocity += Vec3f(0f, 0f, 8f)
            }

            var digging = false
            val running = left.isPressed || right.isPressed
            val strikeInterval = game.balance.mineInterval
            // Adapt dig animation speed to actual dig speed
            val digAnimationSpeed = (model.getAnimationLength("dig", withLastFrameTrimmed = true) / strikeInterval).toFloat()
            if (breakKey.isPressed && !game.visualEditor.enabled) {
                val selectedBlock = getHoveredParent()
                if (selectedBlock is Block) {
                    val target = targetBlock(selectedBlock)
                    if (target != null) {
                        digging = true
                        if (!running) {
                            val targetOrientation = getRotationTo(physics.position, Quatf(), target.pos.toVec3())
                            physics.orientation = targetOrientation
                        }
                        // 200 millseconds because that's the point in the animation where the character strikes. We must divide it by the multiplier of dig
                        // speed relative to the base animation speed, so the strike delay will match the animation's strike delay.
                        mineDelay.strike(strikeDelay = 200.milliseconds / digAnimationSpeed, strikeInterval) {
                            target.health -= game.balance.pickaxeStrength
                        }
                    }
                }
            }
            if (!digging) {
                mineDelay.stopStriking()
            }

            val landing = wasInAirLastFrame && grounded
            wasInAirLastFrame = !grounded

            if (!digging && landing) {
                animation.play("land", loop = false)
            } else if (!digging && isJumping) {
                animation.play("jump", loop = false)
            } else if (running && grounded) {
                animation.play(
                    "walk",
                    specificallyAffectsJoints = legBones
                )
            } else if (!digging && grounded && !animation.animationIsRunning("land")) {
                // Don't play idle while landing, to let the landing animation finish.
                animation.play("active-idle")
            }



            if (digging) {
                animation.play(
                    "dig",
                    specificallyAffectsJoints = upperBodyBones,

                    speed = digAnimationSpeed
                )
            }

            if (!running) {
                animation.stop("walk")
            }


            if (!printedStartupTime) {
                logPerformance("Startup") { "App started in ${timeSinceStartup()}" }
                printedStartupTime = true
            }
        }




        game.physics.system.collision.listen { (a, b) ->
            whenParentFunsTyped<Player, WorldItem>(a, b) { player, item ->
                player.collectItem(item)
            }
        }

    }

    /**
     * Z-up world, canonical forward = –Y.
     * Returns a quaternion that rotates *around the global Z axis only* so the
     * forward direction of [initialRotation] points horizontally toward [targetPoint].
     *
     * The vertical difference between the points is ignored: the result minimises
     * the angle in the X-Y plane.
     */
    private fun getRotationTo(
        point: Vec3f,
        initialRotation: Quatf,
        targetPoint: Vec3f,
    ): Quatf {

        // ── 1. Current forward in world space ───────────────────────────────────────
        val fwdWorld = initialRotation.transform(Vec3f(0f, -1f, 0f))

        // ── 2. Desired direction (point → target) ───────────────────────────────────
        val toTarget = targetPoint - point
        // Quick outs: no distance or purely vertical target
        val toTargetXYlen2 = toTarget.x * toTarget.x + toTarget.y * toTarget.y
        if (toTargetXYlen2 < 1e-8f) return Quatf()   // same spot or vertical above/below

        // ── 3. Project both vectors onto X-Y plane and normalise ────────────────────
        val fwdLen2 = fwdWorld.x * fwdWorld.x + fwdWorld.y * fwdWorld.y
        if (fwdLen2 < 1e-8f) return Quatf()          // forward almost vertical
        val invFwdLen = 1f / sqrt(fwdLen2)
        val invTarLen = 1f / sqrt(toTargetXYlen2)

        val fx = fwdWorld.x * invFwdLen
        val fy = fwdWorld.y * invFwdLen
        val tx = toTarget.x * invTarLen
        val ty = toTarget.y * invTarLen

        // ── 4. Signed angle between the two 2-D unit vectors ───────────────────────
        val dot = fx * tx + fy * ty                   // cos θ
        val cross = fx * ty - fy * tx                   // sin θ   (Z component of 2-D cross)
        val yaw = atan2(cross, dot)                   // range −π … π

        // ── 5. Quaternion that yaws by that angle ──────────────────────────────────
        return Quatf.fromAxisAngle(Vec3f(0f, 0f, 1f), yaw).normalized()
    }

    private fun collectItem(item: WorldItem) {
        val remainder = inventory.insert(item.item)
        if (remainder == 0) {
            // Only destroy the item if there is nothing left
            item.delete()
        } else {
            item.itemCount = remainder
        }
    }


    val blockPos get() = physics.position.toBlockPos()


    /**
     * We target the first block near the player, because the selected block might be "covered" by the perspective of the player character.
     */
    fun targetBlock(directlyHoveredBlock: Block): Block? {
        if (directlyHoveredBlock.pos.squaredDistance(physics.position) > game.balance.breakReach.squared()) return null
        return firstBlockAlong(blockPos.to2D(), directlyHoveredBlock.pos.to2D())
    }

    /**
     * Implemented only for 2D for now.
     * Better solution would use raycasting to find the target block , that would be simpler and work in 3D.
     */
    private fun firstBlockAlong(startPos: IntOffset, endPos: IntOffset): Block? {
        var x0 = startPos.x
        var y0 = startPos.y
        val x1 = endPos.x
        val y1 = endPos.y

        // Δ values
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)

        // Step directions (+1 or -1)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1

        var err = dx + dy      // error term (≈ 0 at perfect diagonal)

        while (true) {
            val pointAlongTheWay = IntOffset(x0, y0)      // current cell
            val block = game.world.blocks[pointAlongTheWay.to2DBlockPos()]
            if (block != null) return block

            // reached destination → stop
            if (x0 == x1 && y0 == y1) break

            val e2 = 2 * err                 // doubled error for comparison
            if (e2 >= dy) {                  // step in X?
                err += dy
                x0 += sx
            }
            if (e2 <= dx) {                  // step in Y?
                err += dx
                y0 += sy
            }
        }
        return null
    }
}

private class DelayedStrike(
    parent: Fun,

    name: String = "RateLimit",
) : Fun(parent.id.child(name), parent) {
    private var strikeStart: Duration? by funValue(null)
    private var lastStrike: Duration? by funValue(null)


    /**
     * Will run [strike], but ONLY if [strikeDelay] has passed since the last time [strike] was successfully invoked.
     * If less time has passed, [strike] will not be invoked at all.
     *
     */
    fun strike(strikeDelay: Duration, strikeInterval: Duration, strike: () -> Unit) {
        if (strikeStart == null) strikeStart = time.gameTime
        else {
            val currentTime = time.gameTime
            if (lastStrike == null) {
                // First strike - happens after strikeDelay
                if (currentTime - strikeStart!! >= strikeDelay) {
                    lastStrike = currentTime
                    strike()
                }
                return
            } else {
                // Subsequent strikes - happens after strikeInterval
                if (time.gameTime - lastStrike!! >= strikeInterval) {
                    lastStrike = currentTime
                    strike()
                }
            }

        }
    }

    fun stopStriking() {
        strikeStart = null
        lastStrike = null
    }
}

inline fun <reified R1, reified R2> whenParentFunsTyped(a: Body, b: Body, callback: (R1, R2) -> Unit) {
    val aRoot = a.getParentFun()
    val bRoot = b.getParentFun()

    // Collect item
    if (aRoot is R1 && bRoot is R2) {
        callback(aRoot, bRoot)
    } else if (aRoot is R2 && bRoot is R1) {
        callback(bRoot, aRoot)
    }
}

fun Body.getParentFun(): Fun? = (this as Fun).parent