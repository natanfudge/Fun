package io.github.natanfudge.fn

class Outer {
    init {
        println("tryft again")
    }
    val x = Separate()
}

class Separate: Loop {
    override fun loop() {
        println("wat")
    }
}


//interface Loopable {}


interface Loop {
    fun loop()

}