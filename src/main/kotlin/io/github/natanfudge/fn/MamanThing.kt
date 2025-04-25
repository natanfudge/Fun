package io.github.natanfudge.fn

fun main() {
//    val makeMult : (Any) -> ((Int) -> (Int) -> Int)    = { maker ->
//        { x->
//            { y->
//                maker as (Any) -> ((Int) -> (Int) -> Int)
//                val self = maker(maker)
//                if (y == 0) 0
//                else {
//                    val firstParamBake = self(x)
//                    val recursiveResult = firstParamBake(y - 1)
//                    recursiveResult + x
//                }
//            }
//        }
//    }

    val makeMult : (Any) -> ((Int) -> (Int) -> Int)    = { maker ->
        { x->
            { y->
                if (y == 0) 0
                else {
                    maker as (Any) -> ((Int) -> (Int) -> Int)
                    maker(maker)(x)(y - 1) + x
                }
            }
        }
    }

    val mult = makeMult(makeMult)

    println(mult(3)(8))
}