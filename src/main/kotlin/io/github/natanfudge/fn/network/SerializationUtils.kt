@file:Suppress("UNCHECKED_CAST")

package io.github.natanfudge.fn.network

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

inline fun <reified T> getSerializerExtended(): KSerializer<T> = when(T::class) {
    Color::class -> ColorSerializer as KSerializer<T>
    else -> serializer<T>()
}

object ColorSerializer: KSerializer<Color> {
    private val surrogate = ColorSurrogate.serializer()
    override val descriptor: SerialDescriptor = surrogate.descriptor

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeSerializableValue(surrogate, ColorSurrogate(value.red, value.green, value.blue, value.alpha))
    }

    override fun deserialize(decoder: Decoder): Color {
        val decoded = decoder.decodeSerializableValue(surrogate)
        return Color(decoded.red, decoded.green, decoded.blue, decoded.alpha)
    }
}

@Serializable
class ColorSurrogate(val red: Float, val green: Float, val blue: Float, val alpha: Float)