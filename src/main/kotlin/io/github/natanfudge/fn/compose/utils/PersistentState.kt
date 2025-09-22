package io.github.natanfudge.fn.compose.utils

import androidx.compose.runtime.*
import com.russhwolf.settings.ExperimentalSettingsApi
import com.russhwolf.settings.Settings
import com.russhwolf.settings.serialization.decodeValueOrNull
import com.russhwolf.settings.serialization.encodeValue
import com.russhwolf.settings.set
import io.github.natanfudge.fn.core.logWarn
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

val settings = Settings()


/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
@Composable
fun rememberPersistentString(key: String, default: @DisallowComposableCalls () -> String): MutableState<String> {
    return remember { persistentString(key, default) }
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
@Composable
fun rememberPersistentInt(key: String, default: @DisallowComposableCalls () -> Int): MutableState<Int> {
    return remember { persistentInt(key, default) }
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
@Composable
fun rememberPersistentBoolean(key: String, default: @DisallowComposableCalls () -> Boolean): MutableState<Boolean> {
    return remember { persistentBoolean(key, default) }
}

@Composable
fun rememberPersistentFloat(key: String, default: @DisallowComposableCalls () -> Float): MutableState<Float> {
    return remember { persistentFloat(key, default) }
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
@Composable
inline fun <reified T> rememberPersistentObject(key: String, crossinline default: @DisallowComposableCalls () -> T): MutableState<T> {
    return remember { persistentObject(key, default) }
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
fun persistentString(key: String, default: @DisallowComposableCalls () -> String): MutableState<String> {
    return mutableStateOf(settings.getStringOrNull(key) ?: default())
        .listen { settings[key] = it }
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
fun persistentDouble(key: String, default: @DisallowComposableCalls () -> Double): MutableState<Double> {
    return mutableStateOf(settings.getDoubleOrNull(key) ?: default())
        .listen { settings[key] = it }
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
fun persistentInt(key: String, default: @DisallowComposableCalls () -> Int): MutableState<Int> {
    return mutableStateOf(settings.getIntOrNull(key) ?: default())
        .listen { settings[key] = it }
}


/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
fun persistentBoolean(key: String, default: @DisallowComposableCalls () -> Boolean): MutableState<Boolean> {
    return mutableStateOf(settings.getBooleanOrNull(key) ?: default())
        .listen { settings[key] = it }
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
fun persistentFloat(key: String, default: @DisallowComposableCalls () -> Float): MutableState<Float> {
    return mutableStateOf(settings.getFloatOrNull(key) ?: default())
        .listen { settings[key] = it }
}

inline fun <reified T> persistentObject(key: String, crossinline default: @DisallowComposableCalls () -> T): MutableState<T> =
    persistentObject(key, serializer()) {
        default()
    }


/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
fun <T> persistentObject(key: String, serializer: KSerializer<T>, default: @DisallowComposableCalls () -> T): MutableState<T> {
    return mutableStateOf(settings.decodeValueOrNull(serializer, key) ?: default()).listen {
        settings.encodeValue(serializer, key, it)
    }
}

inline fun <reified T> persistentStateList(
    key: String, scope: CoroutineScope, debounceDelay: Duration = 1.seconds,
    noinline default: @DisallowComposableCalls () -> List<T> = { mutableListOf() },
): MutableList<T> {
    return persistentStateList(key, serializer<T>(), Debouncer(debounceDelay, scope), default)
}

/**
 * Will store the value of this state in memory by the given [key] and use it. Will be initialized by [default] when no value exists yet.
 */
@OptIn(ExperimentalSerializationApi::class, ExperimentalSettingsApi::class)
fun <T> persistentStateList(key: String, serializer: KSerializer<T>, debouncer: Debouncer, default: @DisallowComposableCalls () -> List<T>): MutableList<T> {
    val listSerializer = ListSerializer(serializer)
    val storedList = try {
        settings.decodeValueOrNull(listSerializer, key)
    } catch (e: SerializationException) {
        logWarn("Serialization", e) { "Failed to decode stored value in key '$key'" }
        settings[key] = null
        null
    }
    val stateList = mutableStateListOf<T>()
    if (storedList != null) stateList.addAll(storedList)
    return ListenedList(stateList) {
        debouncer.submit {
            settings.encodeValue(listSerializer, key, it)
        }
    }
}




