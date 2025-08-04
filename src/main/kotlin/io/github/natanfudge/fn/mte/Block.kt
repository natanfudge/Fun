package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.files.FunImage
import io.github.natanfudge.fn.physics.physics
import io.github.natanfudge.fn.physics.translation
import io.github.natanfudge.fn.render.*
import io.github.natanfudge.fn.util.ceilToInt
import io.github.natanfudge.wgpu4k.matrix.Vec3f

class Block(private val game: DeepSoulsGame, initialType: BlockType?, initialPos: BlockPos?, id: String) : Fun(id) {
    companion object {
        val BreakRenderId = "break"
        val models = BlockType.entries.associateWith {
            Model(Mesh.HomogenousCube, "Block-$it", Material(FunImage.fromResource("drawable/blocks/${it.name.lowercase()}.png")))
        }

        val breakOverlays = (1..5).map {
            Model(Mesh.HomogenousCube, "Damage-$it", Material(FunImage.fromResource("drawable/break/break_$it.png")))
        }
    }

    init {
        val x = 2
    }
    val type by funValue(initialType)
    init {
        @Suppress("RedundantRequireNotNullCall")
        checkNotNull(type)
    }

    val physics = physics(game.physics.system)
    val render by render(models.getValue(type), physics)

    var breakOverlay: FunRenderState? = null

    var health by funValue(type.hardness(game)) {
        afterChange {
            applyDamage(it)
        }
    }

    private fun applyDamage(newHealth: Float) {
        val missingHpFraction = (1 - (newHealth / type.hardness(game))).coerceIn(0f, 1f)
        if (missingHpFraction == 1f) {
            destroy()
            return
        }

        if (missingHpFraction > 0) {
            val damageIndex = (missingHpFraction * 5).ceilToInt().coerceAtMost(5)
            breakOverlay?.close()
            breakOverlay = render(breakOverlays[damageIndex - 1], BreakRenderId, render).apply {
                localTransform.scale = Vec3f(1.01f, 1.01f, 1.01f) // It should be slightly around the cube
            }
        } else {
            breakOverlay?.close()
            breakOverlay = null
        }
    }

    val pos get() = physics.position.toBlockPos()


    init {
        if (initialPos != null) {
            physics.position = initialPos.toVec3()
        }
        physics.affectedByGravity = false
        physics.isImmovable = true
        applyDamage(health)
    }


    fun destroy() {
        if (type == BlockType.Gold) {
            game.world.spawnItem(Item(ItemType.GoldOre, 1), physics.translation)
        }
        game.world.blocks.remove(pos)
        close()
    }

}

