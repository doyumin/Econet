package dev.doyu.econet.paper.admin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 오프라인 플레이어 파일을 읽는 데 필요한 만큼만 구현한 NBT.
 * 서버 내부(NMS) 클래스에 기대면 버전마다 깨지므로, 형식 자체를 직접 읽고 아이템 변환만 Paper API(ItemStack.deserializeBytes)에 맡긴다.
 */
sealed interface NbtTag {
    data class ByteTag(val value: Byte) : NbtTag
    data class ShortTag(val value: Short) : NbtTag
    data class IntTag(val value: Int) : NbtTag
    data class LongTag(val value: Long) : NbtTag
    data class FloatTag(val value: Float) : NbtTag
    data class DoubleTag(val value: Double) : NbtTag
    data class StringTag(val value: String) : NbtTag

    /** 배열은 내용으로 비교되도록 List로 담는다. */
    data class ByteArrayTag(val values: List<Byte>) : NbtTag
    data class IntArrayTag(val values: List<Int>) : NbtTag
    data class LongArrayTag(val values: List<Long>) : NbtTag

    /** 빈 목록도 원래 원소 타입을 지켜야 다시 썼을 때 서버가 같은 값으로 읽는다. */
    data class ListTag(val elementType: Int, val values: List<NbtTag>) : NbtTag

    data class CompoundTag(val values: Map<String, NbtTag>) : NbtTag {
        operator fun get(key: String): NbtTag? = values[key]
    }
}

private const val END = 0
private const val BYTE = 1
private const val SHORT = 2
private const val INT = 3
private const val LONG = 4
private const val FLOAT = 5
private const val DOUBLE = 6
private const val BYTE_ARRAY = 7
private const val STRING = 8
private const val LIST = 9
private const val COMPOUND = 10
private const val INT_ARRAY = 11
private const val LONG_ARRAY = 12

/** gzip으로 압축된 NBT 파일(플레이어 .dat, ItemStack.serializeAsBytes)의 루트 compound를 읽는다. */
fun readCompressedNbt(bytes: ByteArray): NbtTag.CompoundTag =
    DataInputStream(GZIPInputStream(ByteArrayInputStream(bytes))).use { input ->
        val type = input.readUnsignedByte()
        require(type == COMPOUND) { "root tag must be a compound, was $type" }
        input.readUTF()
        input.readPayload(COMPOUND) as NbtTag.CompoundTag
    }

fun writeCompressedNbt(root: NbtTag.CompoundTag): ByteArray {
    val buffer = ByteArrayOutputStream()
    DataOutputStream(GZIPOutputStream(buffer)).use { output ->
        output.writeByte(COMPOUND)
        output.writeUTF("")
        output.writePayload(root)
    }
    return buffer.toByteArray()
}

// NBT 문자열은 Java의 modified UTF-8이라 DataInput/OutputStream의 readUTF/writeUTF가 정확히 맞는다.
private fun DataInputStream.readPayload(type: Int): NbtTag = when (type) {
    BYTE -> NbtTag.ByteTag(readByte())
    SHORT -> NbtTag.ShortTag(readShort())
    INT -> NbtTag.IntTag(readInt())
    LONG -> NbtTag.LongTag(readLong())
    FLOAT -> NbtTag.FloatTag(readFloat())
    DOUBLE -> NbtTag.DoubleTag(readDouble())
    BYTE_ARRAY -> NbtTag.ByteArrayTag(List(readInt()) { readByte() })
    STRING -> NbtTag.StringTag(readUTF())
    LIST -> {
        val elementType = readUnsignedByte()
        NbtTag.ListTag(elementType, List(readInt()) { readPayload(elementType) })
    }
    COMPOUND -> {
        val values = linkedMapOf<String, NbtTag>()
        while (true) {
            val childType = readUnsignedByte()
            if (childType == END) break
            values[readUTF()] = readPayload(childType)
        }
        NbtTag.CompoundTag(values)
    }
    INT_ARRAY -> NbtTag.IntArrayTag(List(readInt()) { readInt() })
    LONG_ARRAY -> NbtTag.LongArrayTag(List(readInt()) { readLong() })
    else -> error("unknown NBT tag type $type")
}

private fun DataOutputStream.writePayload(tag: NbtTag) {
    when (tag) {
        is NbtTag.ByteTag -> writeByte(tag.value.toInt())
        is NbtTag.ShortTag -> writeShort(tag.value.toInt())
        is NbtTag.IntTag -> writeInt(tag.value)
        is NbtTag.LongTag -> writeLong(tag.value)
        is NbtTag.FloatTag -> writeFloat(tag.value)
        is NbtTag.DoubleTag -> writeDouble(tag.value)
        is NbtTag.ByteArrayTag -> {
            writeInt(tag.values.size)
            tag.values.forEach { writeByte(it.toInt()) }
        }
        is NbtTag.StringTag -> writeUTF(tag.value)
        is NbtTag.ListTag -> {
            writeByte(tag.elementType)
            writeInt(tag.values.size)
            tag.values.forEach { writePayload(it) }
        }
        is NbtTag.CompoundTag -> {
            for ((key, value) in tag.values) {
                writeByte(typeOf(value))
                writeUTF(key)
                writePayload(value)
            }
            writeByte(END)
        }
        is NbtTag.IntArrayTag -> {
            writeInt(tag.values.size)
            tag.values.forEach { writeInt(it) }
        }
        is NbtTag.LongArrayTag -> {
            writeInt(tag.values.size)
            tag.values.forEach { writeLong(it) }
        }
    }
}

private fun typeOf(tag: NbtTag): Int = when (tag) {
    is NbtTag.ByteTag -> BYTE
    is NbtTag.ShortTag -> SHORT
    is NbtTag.IntTag -> INT
    is NbtTag.LongTag -> LONG
    is NbtTag.FloatTag -> FLOAT
    is NbtTag.DoubleTag -> DOUBLE
    is NbtTag.ByteArrayTag -> BYTE_ARRAY
    is NbtTag.StringTag -> STRING
    is NbtTag.ListTag -> LIST
    is NbtTag.CompoundTag -> COMPOUND
    is NbtTag.IntArrayTag -> INT_ARRAY
    is NbtTag.LongArrayTag -> LONG_ARRAY
}
