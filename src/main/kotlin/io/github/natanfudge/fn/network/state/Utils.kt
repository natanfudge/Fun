package io.github.natanfudge.fn.network.state

import io.github.natanfudge.fn.network.Fun
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.LongAsStringSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.serializersModuleOf
import kotlinx.serialization.serializer

inline fun <reified T> getFunSerializer() = LongAsStringSerializer as KSerializer<T> // Placeholder

//for now, don't do the serializer stuff, i will figure out a solution for Fun lists/maps later
//private val module = serializ
//
//inline fun <reified T> getFunSerializer()
//
//val module = serializersModuleOf(FunSerializer()).serializer<>()
//
//class FunSerializer: KSerializer<Fun> {
//    override val descriptor: SerialDescriptor
//
//    override fun serialize(encoder: Encoder, value: Fun) {
//    }
//
//    override fun deserialize(decoder: Decoder): Fun {
//    }
//
//}