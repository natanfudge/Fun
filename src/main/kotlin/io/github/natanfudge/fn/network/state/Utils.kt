package io.github.natanfudge.fn.network.state

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.LongAsStringSerializer

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