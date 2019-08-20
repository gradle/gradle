/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.instantexecution.serialization.codecs

import org.gradle.instantexecution.runToCompletion
import org.gradle.instantexecution.serialization.EncodingProvider
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.beans.BeanStateReader
import org.gradle.instantexecution.serialization.withImmediateMode

import org.gradle.internal.reflect.ClassInspector

import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectInputValidation
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable

import java.lang.reflect.Method


/**
 * Instant execution serialization for objects that support [Java serialization][java.io.Serializable]
 * via a custom `writeObject(ObjectOutputStream)` / `readObject(ObjectInputStream)` method pair.
 */
class SerializableWriteObjectCodec : EncodingProducer, Decoding {

    override fun invoke(type: Class<*>): Encoding? =
        writeObjectMethodOf(type)?.let(::WriteObjectEncoding)

    override suspend fun ReadContext.decode(): Any? = withImmediateMode {
        val beanType = readClass()
        val beanStateReader = beanStateReaderFor(beanType)
        beanStateReader.run { newBean() }.also { bean ->
            readObjectMethodOf(beanType).invoke(
                bean,
                ObjectInputStreamAdapter(
                    bean,
                    beanStateReader,
                    this@decode
                )
            )
        }
    }

    private
    fun writeObjectMethodOf(type: Class<*>) = type
        .takeIf { Serializable::class.java.isAssignableFrom(type) }
        ?.firstMatchingMethodOrNull {
            name == "writeObject"
                && parameterCount == 1
                && parameterTypes[0].isAssignableFrom(ObjectOutputStream::class.java)
        }

    private
    fun readObjectMethodOf(type: Class<*>) = readObjectCache.forClass(type)!!

    // TODO:instant-execution readObjectNoData
    private
    val readObjectCache = MethodCache {
        name == "readObject"
            && parameterCount == 1
            && parameterTypes[0].isAssignableFrom(ObjectInputStream::class.java)
    }

    private
    class WriteObjectEncoding(
        private val writeObject: Method
    ) : EncodingProvider<Any> {

        override suspend fun WriteContext.encode(value: Any) {

            val beanType = value.javaClass

            val recordingObjectOutputStream = RecordingObjectOutputStream(beanType, value)
            writeObject.invoke(value, recordingObjectOutputStream)

            writeClass(beanType)
            recordingObjectOutputStream.run {
                playback()
            }
        }
    }
}


private
class RecordingObjectOutputStream(

    private
    val beanType: Class<*>,

    private
    val bean: Any

) : ObjectOutputStream() {

    private
    val operations = mutableListOf<suspend WriteContext.() -> Unit>()

    suspend fun WriteContext.playback() {
        operations.forEach { operation ->
            operation()
        }
    }

    private
    fun record(operation: suspend WriteContext.() -> Unit) {
        operations.add(operation)
    }

    override fun defaultWriteObject() = record {
        beanStateWriterFor(beanType).run {
            writeStateOf(bean)
        }
    }

    override fun writeInt(`val`: Int) = record {
        writeInt(`val`)
    }

    override fun writeUTF(str: String) = record {
        writeString(str)
    }

    override fun writeObjectOverride(obj: Any?) = record {
        write(obj)
    }

    override fun write(`val`: Int) = record {
        outputStream.write(`val`)
    }

    override fun write(buf: ByteArray) = record {
        outputStream.write(buf)
    }

    override fun write(buf: ByteArray, off: Int, len: Int) = record {
        outputStream.write(buf, off, len)
    }

    override fun writeByte(`val`: Int) = record {
        writeByte(`val`.toByte())
    }

    override fun writeChar(`val`: Int) = record {
        writeInt(`val`)
    }

    override fun writeBoolean(`val`: Boolean) = record {
        writeBoolean(`val`)
    }

    override fun writeLong(`val`: Long) = record {
        writeLong(`val`)
    }

    override fun useProtocolVersion(version: Int) = Unit

    override fun flush() = Unit

    override fun close() = Unit

    override fun reset() = TODO("reset")

    override fun writeFields() = TODO("writeFields")

    override fun putFields(): PutField = TODO("putFields")

    override fun writeChars(str: String) = TODO("writeChars")

    override fun writeUnshared(obj: Any?) = TODO("writeUnshared")

    override fun writeShort(`val`: Int) = TODO("writeShort")

    override fun writeBytes(str: String) = TODO("writeBytes")

    override fun writeDouble(`val`: Double) = TODO("writeDouble")

    override fun writeFloat(`val`: Float) = TODO("writeFloat")
}


private
class ObjectInputStreamAdapter(

    private
    val bean: Any,

    private
    val beanStateReader: BeanStateReader,

    private
    val readContext: ReadContext

) : ObjectInputStream() {

    override fun defaultReadObject() = runToCompletion {
        beanStateReader.run {
            readContext.readStateOf(bean)
        }
    }

    override fun readObjectOverride(): Any? = runToCompletion {
        readContext.read()
    }

    override fun readInt(): Int = readContext.readInt()

    override fun readUTF(): String = readContext.readString()

    override fun read(b: ByteArray): Int = inputStream.read(b)

    override fun markSupported(): Boolean = inputStream.markSupported()

    override fun mark(readlimit: Int) = inputStream.mark(readlimit)

    override fun read(): Int = inputStream.read()

    override fun readChar(): Char = readContext.readInt().toChar()

    override fun readLong(): Long = readContext.readLong()

    override fun readBoolean(): Boolean = readContext.readBoolean()

    override fun readNBytes(len: Int): ByteArray = inputStream.readNBytes(len)

    override fun readNBytes(b: ByteArray, off: Int, len: Int): Int = inputStream.readNBytes(b, off, len)

    override fun skip(n: Long): Long = inputStream.skip(n)

    override fun registerValidation(obj: ObjectInputValidation?, prio: Int) = Unit

    override fun close() = Unit

    override fun available(): Int = inputStream.available()

    override fun skipBytes(len: Int): Int = inputStream.skip(len.toLong()).toInt()

    override fun read(buf: ByteArray, off: Int, len: Int): Int = TODO("read")

    override fun readLine(): String = TODO("readLine")

    override fun readByte(): Byte = TODO("readByte")

    override fun readFully(buf: ByteArray) = TODO("readFully")

    override fun readFully(buf: ByteArray, off: Int, len: Int) = TODO("readFully")

    override fun readDouble(): Double = TODO("readDouble")

    override fun readUnshared(): Any = TODO("readUnshared")

    override fun readShort(): Short = TODO("readShort")

    override fun readUnsignedShort(): Int = TODO("readUnsignedShort")

    override fun readUnsignedByte(): Int = TODO("readUnsignedByte")

    override fun readFloat(): Float = TODO("readFloat")

    override fun readFields(): GetField = TODO("readFields")

    override fun transferTo(out: OutputStream): Long = TODO("transferTo")

    override fun readAllBytes(): ByteArray = TODO("readAllBytes")

    override fun reset() = TODO("reset")

    private
    val inputStream: InputStream
        get() = readContext.inputStream
}


// TODO:instant-execution extract SerializableWriteReplaceCodec from BeanCodec and reuse this
internal
class MethodCache(

    private
    val predicate: Method.() -> Boolean

) {
    private
    val methodCache = hashMapOf<Class<*>, Method?>()

    fun forObject(value: Any) =
        forClass(value.javaClass)

    fun forClass(type: Class<*>) =
        methodCache.computeIfAbsent(type) { it.firstMatchingMethodOrNull(predicate) }
}


internal
fun Class<*>.firstMatchingMethodOrNull(predicate: Method.() -> Boolean): Method? =
    ClassInspector.inspect(this)
        .allMethods
        .find(predicate)
        ?.apply { isAccessible = true }
