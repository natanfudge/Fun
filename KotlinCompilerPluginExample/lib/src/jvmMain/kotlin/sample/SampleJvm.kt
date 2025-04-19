package sample

actual class Sample {
    actual fun checkMe() = 42
}

actual object Platform {
    actual val name: String = "JVM"
}

class Y: Bar()

fun main() {
    println("Res 1: ")
    println(Y().name)
    /**
     * The compiler plugin will replace this with create<MyTest>(_MyTestProvider)
     */
//    val myTest = create<MyTest>()
//    myTest.print()
}