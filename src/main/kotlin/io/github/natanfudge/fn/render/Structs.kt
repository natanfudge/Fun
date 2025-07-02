@file:Suppress("UNCHECKED_CAST")
@file:OptIn(ExperimentalUnsignedTypes::class)

package io.github.natanfudge.fn.render

import io.github.natanfudge.fn.webgpu.WebGPUContext
import io.ygdrasil.webgpu.GPUBufferUsage
import java.nio.ByteBuffer
import java.nio.ByteOrder

interface GPUStructDescriptor {
    val size: UInt

    fun createBuffer(ctx: WebGPUContext, initialSize: UInt, expandable: Boolean, vararg usage: GPUBufferUsage): ManagedGPUMemory {
        return ManagedGPUMemory(ctx, initialSizeBytes = (initialSize * size.toULong()), expandable = expandable, *usage)
    }

    fun fullElements(array: ManagedGPUMemory): ULong = array.fullBytes / size
}

class GPUStructInstance<T : GPUStructDescriptor>(
    internal val buffer: ByteArray,
    internal val descriptor: T,
)

private fun layOut(vararg datatypes: DataType<*>): UIntArray {
    val layout = UIntArray(datatypes.size)
    var offset = 0u
    var potentialAlignmentSpace = 0u
    datatypes.forEachIndexed { i, type ->
        var smallType = false
        if (type.actualSize <= potentialAlignmentSpace) {
            // If the current value can fit within the empty alignment space of the previous element, fit it in
            potentialAlignmentSpace -= type.actualSize
            smallType = true
        } else {
            // Otherwise, skip this space. (This is just the rules of how WebGPU structs work)
            offset += potentialAlignmentSpace
        }
        layout[i] = offset
        offset += type.actualSize
        val paddingSpace = type.alignSize - type.actualSize
        // For future compatibility, we'll try filling in multiple small values in the padding space if possible.
        if (!smallType) {
            potentialAlignmentSpace = paddingSpace
        }
    }
    return layout
}

abstract class Struct1<T1, S : Struct1<T1, S>>(
    val t1: DataType<T1>,
) : GPUStructDescriptor {

    val layout = layOut(t1)
    override val size = (t1.alignSize).wgpuAlignInt()

    private fun toArray(a: T1) = concatDifferentArrays(
        size, layout, t1.toArray(a),
    )

    operator fun invoke(a: T1): GPUStructInstance<S> {
        return GPUStructInstance(
            toArray(a),
            this
        ) as GPUStructInstance<S>
    }

    fun new(mem: ManagedGPUMemory, a: T1): GPUPointer<S> {
        return mem.new(toArray(a)) as GPUPointer<S>
    }

    fun setFirst(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1) {
        mem.write(t1.toArray(a), pointer)
    }


}

abstract class Struct2<T1, T2, S : Struct2<T1, T2, S>>(
    val t1: DataType<T1>,
    val t2: DataType<T2>,
) : GPUStructDescriptor {

    val layout = layOut(t1, t2)
    override val size = (layout.last() + t2.alignSize).wgpuAlignInt()

    private fun toArray(a: T1, b: T2) = concatDifferentArrays(
        size, layout, t1.toArray(a), t2.toArray(b)
    )

    operator fun invoke(a: T1, b: T2): GPUStructInstance<S> {
        return GPUStructInstance(
            toArray(a, b),
            this
        ) as GPUStructInstance<S>
    }

    fun new(mem: ManagedGPUMemory, a: T1, b: T2): GPUPointer<S> {
        return mem.new(toArray(a, b)) as GPUPointer<S>
    }

    fun set(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1, b: T2) {
        mem.write(toArray(a, b), pointer)
    }

    fun setFirst(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1) {
        mem.write(t1.toArray(a), pointer)
    }

    fun setSecond(mem: ManagedGPUMemory, pointer: GPUPointer<S>, b: T2) {
        mem.write(t2.toArray(b), pointer + layout[1])
    }
}


abstract class Struct4<T1, T2, T3, T4, S : Struct4<T1, T2, T3, T4, S>>(
    val t1: DataType<T1>,
    val t2: DataType<T2>,
    val t3: DataType<T3>,
    val t4: DataType<T4>,
) : GPUStructDescriptor {

    val layout = layOut(t1, t2, t3, t4)
    override val size = (layout.last() + t4.alignSize).wgpuAlignInt()

    private fun toArray(a: T1, b: T2, c: T3, d: T4) = concatDifferentArrays(
        size, layout, t1.toArray(a), t2.toArray(b), t3.toArray(c), t4.toArray(d)
    )

    operator fun invoke(a: T1, b: T2, c: T3, d: T4): GPUStructInstance<S> {
        return GPUStructInstance(
            toArray(a, b, c, d),
            this
        ) as GPUStructInstance<S>
    }

    fun new(mem: ManagedGPUMemory, a: T1, b: T2, c: T3, d: T4): GPUPointer<S> {
        return mem.new(toArray(a, b, c, d)) as GPUPointer<S>
    }

    fun setFirst(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1) {
        mem.write(t1.toArray(a), pointer)
    }

    fun setSecond(mem: ManagedGPUMemory, pointer: GPUPointer<S>, b: T2) {
        mem.write(t2.toArray(b), pointer + layout[1])
    }

    fun setThird(mem: ManagedGPUMemory, pointer: GPUPointer<S>, c: T3) {
        mem.write(t3.toArray(c), pointer + layout[2])
    }

    fun setFourth(mem: ManagedGPUMemory, pointer: GPUPointer<S>, d: T4) {
        mem.write(t4.toArray(d), pointer + layout[3])
    }

}

abstract class Struct5<T1, T2, T3, T4, T5, S : Struct5<T1, T2, T3, T4, T5, S>>(
    val t1: DataType<T1>,
    val t2: DataType<T2>,
    val t3: DataType<T3>,
    val t4: DataType<T4>,
    val t5: DataType<T5>,
) : GPUStructDescriptor {

    val layout = layOut(t1, t2, t3, t4, t5)
    override val size = (layout.last() + t5.alignSize).wgpuAlignInt()

    private fun toArray(a: T1, b: T2, c: T3, d: T4, e: T5) = concatDifferentArrays(
        size, layout, t1.toArray(a), t2.toArray(b), t3.toArray(c),
        t4.toArray(d), t5.toArray(e)
    )

    operator fun invoke(a: T1, b: T2, c: T3, d: T4, e: T5): GPUStructInstance<S> {
        return GPUStructInstance(
            toArray(a, b, c, d, e),
            this
        ) as GPUStructInstance<S>
    }

    fun new(mem: ManagedGPUMemory, a: T1, b: T2, c: T3, d: T4, e: T5): GPUPointer<S> {
        return mem.new(toArray(a, b, c, d, e)) as GPUPointer<S>
    }

    fun set(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1, b: T2, c: T3, d: T4, e: T5) {
        mem.write(toArray(a, b, c, d, e), pointer)
    }

    fun setFirst(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1) {
        mem.write(t1.toArray(a), pointer)
    }

    fun setSecond(mem: ManagedGPUMemory, pointer: GPUPointer<S>, b: T2) {
        mem.write(t2.toArray(b), pointer + layout[1])
    }

    fun setThird(mem: ManagedGPUMemory, pointer: GPUPointer<S>, c: T3) {
        mem.write(t3.toArray(c), pointer + layout[2])
    }

    fun setFourth(mem: ManagedGPUMemory, pointer: GPUPointer<S>, d: T4) {
        mem.write(t4.toArray(d), pointer + layout[3])
    }

    fun setFirth(mem: ManagedGPUMemory, pointer: GPUPointer<S>, e: T5) {
        mem.write(t5.toArray(e), pointer + layout[4])
    }

}


abstract class Struct6<T1, T2, T3, T4, T5, T6, S : Struct6<T1, T2, T3, T4, T5, T6, S>>(
    val t1: DataType<T1>,
    val t2: DataType<T2>,
    val t3: DataType<T3>,
    val t4: DataType<T4>,
    val t5: DataType<T5>,
    val t6: DataType<T6>,
) : GPUStructDescriptor {

    val layout = layOut(t1, t2, t3, t4, t5, t6)
    override val size = (layout.last() + t6.alignSize).wgpuAlignInt()

    private fun toArray(a: T1, b: T2, c: T3, d: T4, e: T5, f: T6) = concatDifferentArrays(
        size, layout, t1.toArray(a), t2.toArray(b), t3.toArray(c),
        t4.toArray(d), t5.toArray(e), t6.toArray(f),
    )

    operator fun invoke(a: T1, b: T2, c: T3, d: T4, e: T5, f: T6): GPUStructInstance<S> {
        return GPUStructInstance(
            toArray(a, b, c, d, e, f),
            this
        ) as GPUStructInstance<S>
    }

    fun new(mem: ManagedGPUMemory, a: T1, b: T2, c: T3, d: T4, e: T5, f: T6): GPUPointer<S> {
        return mem.new(toArray(a, b, c, d, e, f)) as GPUPointer<S>
    }

    fun set(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1, b: T2, c: T3, d: T4, e: T5, f: T6) {
        mem.write(toArray(a, b, c, d, e, f), pointer)
    }

    fun setFirst(mem: ManagedGPUMemory, pointer: GPUPointer<S>, a: T1) {
        mem.write(t1.toArray(a), pointer)
    }

    fun setSecond(mem: ManagedGPUMemory, pointer: GPUPointer<S>, b: T2) {
        mem.write(t2.toArray(b), pointer + layout[1])
    }

    fun setThird(mem: ManagedGPUMemory, pointer: GPUPointer<S>, c: T3) {
        mem.write(t3.toArray(c), pointer + layout[2])
    }

    fun setFourth(mem: ManagedGPUMemory, pointer: GPUPointer<S>, d: T4) {
        mem.write(t4.toArray(d), pointer + layout[3])
    }

    fun setFirth(mem: ManagedGPUMemory, pointer: GPUPointer<S>, e: T5) {
        mem.write(t5.toArray(e), pointer + layout[4])
    }

    fun setSixth(mem: ManagedGPUMemory, pointer: GPUPointer<S>, f: T6) {
        mem.write(t6.toArray(f), pointer + layout[5])
    }


}


private fun concatDifferentArrays(resArraySize: UInt, layout: UIntArray, vararg arrays: Any): ByteArray {
    val buffer = createMemoryBuffer(resArraySize)
    arrays.forEachIndexed { i, array ->
        val offset = layout[i]
        buffer.putArray(offset.toInt(), array)
    }
    return buffer.array()
}


private fun createMemoryBuffer(size: UInt): KByteBuffer {
    val array = ByteArray(size.toInt())
    // GPU uses little endian apparently
    val buffer = ByteBuffer.wrap(array).order(ByteOrder.LITTLE_ENDIAN)
    return buffer
}

private fun ByteBuffer.putArray(offset: Int, array: Any) = when (array) {
    is IntArray -> asIntBuffer().put(offset / 4, array)
    is ByteArray -> put(offset, array)
    is FloatArray -> asFloatBuffer().put(offset / 4, array)
    else -> error("Unsupported array type $this")
}
