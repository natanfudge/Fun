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
) {

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
val GameBalance by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val balanceFile = URI(Res.getUri("files/balance.json5")).toPath().readText()
    json.decodeFromString<Balance>(balanceFile)
}

val BlockType.hardness: Float get() = GameBalance.blocks[this]?.hardness ?: 1f
val BlockType.zHeight: Int get() = GameBalance.blocks[this]?.zHeight ?: 0
val BlockType.spawnPrec: Float get() = GameBalance.blocks[this]?.spawnPrec ?: 0f
val BlockType.veinSizeMin: Int get() = GameBalance.blocks[this]?.veinSizeMin ?: 0
val BlockType.veinSizeMax: Int get() = GameBalance.blocks[this]?.veinSizeMax ?: 0

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
