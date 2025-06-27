package io.github.natanfudge.fn.mte

import korlibs.time.milliseconds

object Balance {
    fun blockHardness(type: BlockType): Float = when (type) {
        BlockType.Dirt -> 2f
        BlockType.Gold -> 6f
    }

    val MineInterval = 500.milliseconds

    val BreakReach = 5

    val PickaxeStrength = 1f
}

enum class BlockType {
    Dirt, Gold
}