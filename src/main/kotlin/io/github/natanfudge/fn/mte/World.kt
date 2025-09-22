package io.github.natanfudge.fn.mte

import io.github.natanfudge.fn.compose.funedit.ValueEditor
import io.github.natanfudge.fn.core.Fun
import io.github.natanfudge.fn.core.FunId
import io.github.natanfudge.fn.network.state.FunList
import io.github.natanfudge.fn.network.state.FunMap
import io.github.natanfudge.fn.network.state.getFunSerializer
import io.github.natanfudge.wgpu4k.matrix.Vec3f
import kotlin.math.roundToInt
import kotlin.random.Random

private data class PositionedBlock(
    val type: BlockType,
    val pos: BlockPos,
)

fun describeGame(): String {
    return """
        You are playing a 2D mining game, akin to Terraria. 
    """.trimIndent()
}

class World(val game: DeepSoulsGame) : Fun("World") {
    private val width = 21
    private val height = 21

    private inline fun roll(chancePct: Float, crossinline callback: () -> Unit) {
        if ((0..99).random() < chancePct * 100) {
            callback()
        }
    }

//    fun worldgenSimple(): List<Block> {
//        return List(width * height) {
//            val x = it % width - width / 2
//            val z = it / width - height + DeepSoulsGame.SurfaceZ
//            val type = if (Random.nextInt(1, 11) == 10) BlockType.Gold else BlockType.Dirt
//            Block(
//                game,
//                type,
//                BlockPos(x = x, y = 0, z = z), id = "Block-$type-$x-$z"
//            )
//        }
//    }

    fun worldgen(mapWidth: Int, zLevelStart: Int, zLevelEnd: Int, xLevelStart: Int): List<Block> {
        val blocksList = BlockType.entries

        val validBlocks = blocksList.filter { it.zHeight(game) in zLevelStart until zLevelEnd }
        val height = zLevelEnd - zLevelStart
        var fillerBlock: BlockType = BlockType.Dirt
//        if (zLevelStart == 0) {
//            fillerBlock = BlockType.Dirt
//        } else {
//            TO DO("")
//        }


        val matrix: List<MutableList<PositionedBlock>> = List(height) { z ->
            MutableList(mapWidth) { x ->
                PositionedBlock(fillerBlock, BlockPos(x = x, y = 0, z = z))
            }
        }
        val weightedBlocksList = validBlocks.flatMap { block ->
            val weight = (block.spawnPrec(game) * 10000).roundToInt().coerceAtLeast(1)
            List(weight) { block }
        }

        for (z in 0 until height) {
            for (x in 0 until mapWidth) {
                roll(0.10f) {
//                    val block = weightedBlocksList.random()
                    val block = if(z < 5) BlockType.Dirt else BlockType.Gold
                    val veinSize = (block.veinSizeMin(game)..block.veinSizeMax(game)).random()
                    val placed = mutableSetOf<Pair<Int, Int>>()
                    placed.add(Pair(x, z))
                    while (placed.size < veinSize) {
                        val vblock = placed.random()
                        val vx = vblock.first + (-1..1).random()
                        val vz = vblock.second + (-1..1).random()
                        if (vx in 0 until mapWidth && vz in 0 until height && Pair(vx, vz) !in placed) {
                            placed.add(Pair(vx, vz))
                            val list = matrix[vz]
                            list[vx] = PositionedBlock(
                                block, BlockPos(x = vx, z = vz, y = 0)
                            )
                        }
                    }
                }
            }
        }

        return matrix.flatten().map {
            Block(
                game, it.type, it.pos.copy(x = it.pos.x + xLevelStart , z = it.pos.z + zLevelStart ),
                id = "Block-${it.type.name}-${it.pos.x}-${it.pos.z}"
            )
        }
    }

    val blocks = mapOfFuns<BlockPos, Block>("blocks") {
        Block(game, null, null, it)
    }

    init {
        if (blocks.isEmpty()) {
            blocks.putAll(worldgen(width, DeepSoulsGame.SurfaceZ - height, DeepSoulsGame.SurfaceZ, -10).associateBy { it.pos })
        }
    }

    val items = listOfFuns<WorldItem>("items") {
        WorldItem(game, it, null)
    }

    fun spawnItem(item: Item, pos: Vec3f) {
        items.add(WorldItem(game, "Item-${item.type}-${items.size}-${time.gameTime}", item).apply {
            physics.position = pos
        })
    }

    fun initialize() {
        repeat(1) {
            spawnItem(Item(ItemType.GoldOre, (it + 1) * 4), game.player.physics.position + Vec3f(2f + it, 0f, 0f))
        }
    }
}

@Suppress("UNCHECKED_CAST")
inline fun <reified K, reified V : Fun> Fun.mapOfFuns(name: String, constructor: (FunId) -> V): FunMap<K, V> {
    // SLOW: too much code in inline function
    val oldState = stateManager.getState(id)?.getCurrentState()?.get(name)?.value
    val keySerializer = getFunSerializer<K>()
    val valueSerializer = getFunSerializer<V>()
    val map = if (oldState is Map<*, *> && oldState.all { it.value is V }) {
        val converted = oldState.mapValues {
            constructor((it.value as Fun).id)
        } as Map<K, V>
        FunMap(converted.toMutableMap(), name, this, keySerializer, valueSerializer)
    } else {
        if (oldState != null) println("Throwing out incompatible old state for $id:$name")
        FunMap(mutableMapOf(), name, this, keySerializer, valueSerializer)
    }
    stateManager.registerState(id, name, map)
    return map
}


inline fun <reified T : Fun> Fun.listOfFuns(name: String, constructor: (FunId) -> T): FunList<T> {
    // SLOW: too much code in inline function
    val oldState = stateManager.getState(id)?.getCurrentState()?.get(name)?.value
    val serializer = getFunSerializer<T>()
    val list = if (oldState is List<*> && oldState.all { it is T }) {
        val converted = oldState.map {
            constructor((it as Fun).id)
        }
        FunList(converted.toMutableList(), name, this.id, serializer, ValueEditor.Missing as ValueEditor<List<T>>)
    } else {
        if (oldState != null) println("Throwing out incompatible old state for $id:$name")
        FunList(mutableListOf(), name, this.id, serializer, ValueEditor.Missing as ValueEditor<List<T>>)
    }
    stateManager.registerState(id, name, list)
    return list
}
