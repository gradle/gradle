/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs.jos

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.configurationcache.extensions.documentationLinkFor
import org.gradle.configurationcache.problems.DocumentationSection
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.beans.BeanStateReader
import org.gradle.configurationcache.serialization.readDouble
import org.gradle.configurationcache.serialization.readFloat
import org.gradle.configurationcache.serialization.readShort
import org.gradle.configurationcache.serialization.runReadOperation
import java.io.InputStream
import java.io.ObjectInputStream
import java.io.ObjectInputValidation
import java.io.OutputStream


internal
class ObjectInputStreamAdapter(

    private
    val bean: Any,

    private
    val beanStateReader: BeanStateReader,

    private
    val readContext: ReadContext

) : ObjectInputStream() {

    override fun defaultReadObject() = beanStateReader.run {
        readContext.runReadOperation {
            readStateOf(bean)
        }
    }

    override fun readObjectOverride(): Any? = readContext.runReadOperation {
        read()
    }

    override fun readInt(): Int = readContext.readInt()

    override fun readUTF(): String = readContext.readString()

    override fun read(b: ByteArray): Int = inputStream.read(b)

    override fun markSupported(): Boolean = inputStream.markSupported()

    override fun mark(readlimit: Int) = inputStream.mark(readlimit)

    override fun reset() = inputStream.reset()

    override fun read(): Int = inputStream.read()

    override fun readChar(): Char = readContext.readInt().toChar()

    override fun readUnsignedByte(): Int = readByte().let {
        require(it >= 0)
        it.toInt()
    }

    override fun readByte(): Byte = readContext.readByte()

    override fun readUnsignedShort(): Int = readShort().let {
        require(it >= 0)
        it.toInt()
    }

    override fun readShort(): Short = readContext.readShort()

    override fun readLong(): Long = readContext.readLong()

    override fun readFloat(): Float = readContext.readFloat()

    override fun readDouble(): Double = readContext.readDouble()

    override fun readBoolean(): Boolean = readContext.readBoolean()

    // TODO:configuration-cache override Java 11 API for compatibility with Java 11
    // override fun readNBytes(len: Int): ByteArray = inputStream.readNBytes(len)

    override fun readNBytes(b: ByteArray, off: Int, len: Int): Int = inputStream.readNBytes(b, off, len)

    override fun skip(n: Long): Long = inputStream.skip(n)

    override fun registerValidation(obj: ObjectInputValidation?, prio: Int) = Unit

    override fun close() = Unit

    override fun available(): Int = inputStream.available()

    override fun skipBytes(len: Int): Int = inputStream.skip(len.toLong()).toInt()

    override fun read(buf: ByteArray, off: Int, len: Int): Int = inputStream.read(buf, off, len)

    override fun readLine(): String = unsupported("ObjectInputStream.readLine")

    override fun readFully(buf: ByteArray) = unsupported("ObjectInputStream.readFully")

    override fun readFully(buf: ByteArray, off: Int, len: Int) = unsupported("ObjectInputStream.readFully")

    override fun readUnshared(): Any = unsupported("ObjectInputStream.readUnshared")

    override fun readFields(): GetField = unsupported("ObjectInputStream.readFields")

    override fun transferTo(out: OutputStream): Long = unsupported("ObjectInputStream.transferTo")

    override fun readAllBytes(): ByteArray = unsupported("ObjectInputStream.readAllBytes")

    private
    val inputStream: InputStream
        get() = readContext.inputStream
}


internal
fun unsupported(feature: String): Nothing =
    throw UnsupportedOperationException("'$feature' is not supported by the Gradle configuration cache. See $javaSerializationDocumentationLink for details.")


private
val javaSerializationDocumentationLink: String
    get() = DocumentationRegistry().documentationLinkFor(DocumentationSection.NotYetImplementedJavaSerialization)
