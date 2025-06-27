package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.network.Fun
import io.github.natanfudge.fn.network.state.funList
import io.github.natanfudge.fn.network.state.funMap
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.random.Random

class World(val game: MineTheEarth) : Fun("World", game.context) {
    private val width = 21
    private val height = 21

    val blocks = funMap<BlockPos, Block>(
        "blocks",
        List(height * width) {
            val x = it % width
            val y = it / width
            val type = if (Random.nextInt(1, 11) == 10) BlockType.Gold else BlockType.Dirt
            Block(game, type, BlockPos(x - width / 2, y = 0, z = y - height / 2))
        }.associateBy { it.pos })

    val items = funList<WorldItem>("items")

    fun spawnItem(item: Item, pos: Vec3f) {
        items.add(WorldItem(game, item, pos))
    }

    init {
        repeat(10) {
            spawnItem(Item(ItemType.GoldOre, (it + 1) * 4), game.player.physics.translation + Vec3f(2f + it, 0f, 0f))
        }
    }
}