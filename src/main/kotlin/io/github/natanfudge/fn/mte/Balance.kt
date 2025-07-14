package io.github.natanfudge.fn.mte

import korlibs.time.milliseconds
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import natan.`fun`.generated.resources.Res
import java.net.URI
import kotlin.io.path.readText
import kotlin.io.path.toPath

@Serializable
data class Balance(
    val blocks: Map<BlockType, BlockBalance>,
    val mineIntervalMs: Long,
    val breakReach: Int,
    val pickaxeStrength: Float,
    val undergroundStart: Int,
    val cavernsStart: Int,
    val deepCavernsStart: Int,
    val allmostHellStart: Int,
    val hellStart: Int,
    val worldEnd: Int,
) {
    companion object {
        fun create() : Balance {
            val balanceFile = URI(Res.getUri("files/balance.json5")).toPath().readText()
            return json.decodeFromString<Balance>(balanceFile)
        }
    }

    val mineInterval get() = mineIntervalMs.milliseconds
}

@Serializable
data class BlockBalance(
    val hardness: Float,
    val zHeight: Int,
    val spawnPrec: Float,
    val veinSizeMin: Int,
    val veinSizeMax: Int
)


@Serializable
enum class BlockType {
    Dirt, Gold,
}

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

fun BlockType.hardness(game: DeepSoulsGame): Float = game.balance.blocks[this]?.hardness ?: 1f
fun BlockType.zHeight(game: DeepSoulsGame): Int = (game.balance.blocks[this]?.zHeight) ?: 0
fun BlockType.spawnPrec(game: DeepSoulsGame): Float= game.balance.blocks[this]?.spawnPrec ?: 0f
fun BlockType.veinSizeMin(game: DeepSoulsGame): Int = game.balance.blocks[this]?.veinSizeMin ?: 0
fun BlockType.veinSizeMax(game: DeepSoulsGame): Int = game.balance.blocks[this]?.veinSizeMax ?: 0

//object Balance {
//    fun blockHardness(type: BlockType): Float = when (type) {
//        BlockType.Dirt -> 2f
//        BlockType.Gold -> 6f
//        else -> error("")
//    }
//
//    val MineInterval = 500.milliseconds
//
//    val BreakReach = 5
//
//    val PickaxeStrength = 1f
//}
//
//val BlockType.zHeight : Int get() = when(this) {
//    BlockType.Dirt -> 1
//    BlockType.Gold -> 2
//
//}
//
//val BlockType.spawnPrec : Int get() = when(this) {
//    BlockType.Dirt -> 1
//    BlockType.Gold -> 2
//
//}
//
//val BlockType.veinSize : IntRange get() = when(this) {
//    BlockType.Dirt -> 1..4
//    BlockType.Gold -> 2..3
//
//}
//
