This:
```kotlin
class UserFacing : SomeFun("1", FunClient()) {
    val x by funState(1)
}
```

Needs to be transformed to this:

```kotlin
class CompilerGenerated : SomeFun("1", FunClient()) {
    internal var _x = 0
    var x: Int
        get() = _x
        set(value) {
            _x = value
            updateOthers("x", value, Int.serializer())
        }
    


    fun applyChange(key: String, value: NetworkValue) {
        when (key) {
            "x" -> _x = value.decode(Int.serializer())
            else -> error("Unknown property $key")
        }
    }
}
```

