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

import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.withBeanTrace
import java.io.ObjectOutputStream


internal
class RecordingObjectOutputStream(

    private
    val beanType: Class<*>,

    private
    val bean: Any

) : ObjectOutputStream() {

    private
    val operations = mutableListOf<suspend WriteContext.() -> Unit>()

    suspend fun WriteContext.playback() {
        withBeanTrace(beanType) {
            operations.forEach { operation ->
                operation()
            }
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

    override fun writeShort(`val`: Int) = record {
        this.writeShort(`val`.toShort())
    }

    override fun writeLong(`val`: Long) = record {
        writeLong(`val`)
    }

    override fun writeFloat(`val`: Float) = record {
        this.writeFloat(`val`)
    }

    override fun writeDouble(`val`: Double) = record {
        this.writeDouble(`val`)
    }

    override fun useProtocolVersion(version: Int) = Unit

    override fun flush() = Unit

    override fun close() = Unit

    override fun reset() = unsupported("ObjectOutputStream.reset")

    override fun writeFields() = unsupported("ObjectOutputStream.writeFields")

    override fun putFields(): PutField = unsupported("ObjectOutputStream.putFields")

    override fun writeChars(str: String) = unsupported("ObjectOutputStream.writeChars")

    override fun writeBytes(str: String) = unsupported("ObjectOutputStream.writeBytes")

    override fun writeUnshared(obj: Any?) = unsupported("ObjectOutputStream.writeUnshared")
}
