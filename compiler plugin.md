This:
```kotlin
class UserFacing : SomeFun("1", FunClient()) {
    val x by funState(1)
}
```

Needs to be transformed to this:

```kotlin


class CompilerGenerated : SomeFun("1", FunClient()), FunStateHolder {
    internal var _x = 0
    var x: Int
        get() = _x
        set(value) {
            _x = value
            updateOthers("x", value, Int.serializer())
        }

    val coins = funList<Int>("coins")
    

    override fun toMap(): Map<String, Any?> {
        return mapOf("x" to x, "coins" to coins)
    }

    override fun setValue(key: String, change: StateChange) {
        when (key) {
            "x" -> _x = (change as? StateChange.SetProperty ?: error("Expected only SET-PROPERTY for property"))
                .decode(Int.serializer())
            "coins" -> {
                when (change) {
                    is StateChange.SingleChange -> {
                        val deserialized = change.value.decode(Int.serializer())
                        when (change) {
                            is StateChange.ListSet -> coins.setDirectly(change.index, deserialized)
                            is StateChange.SetProperty -> error("Unexpected SET-PROPERTY for list")
                        }
                    }

                    is StateChange.ListChange -> TODO()
                }
            }
            else -> error("Unknown property $key")
        }
    }
}
```

