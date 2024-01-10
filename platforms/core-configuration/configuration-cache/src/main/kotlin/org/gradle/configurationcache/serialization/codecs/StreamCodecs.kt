/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.configurationcache.serialization.codecs

import org.gradle.configurationcache.problems.StructuredMessageBuilder
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.ReadContext
import org.gradle.configurationcache.serialization.WriteContext
import org.gradle.configurationcache.serialization.logUnsupported
import org.gradle.configurationcache.serialization.readEnum
import org.gradle.configurationcache.serialization.writeEnum
import java.io.InputStream
import java.io.OutputStream


private
enum class StreamReference {
    IN, OUT, ERR, UNSUPPORTED
}


object InputStreamCodec : Codec<InputStream> {
    override suspend fun WriteContext.encode(value: InputStream) {
        if (value !== System.`in`) {
            logUnsupported("serialize", InputStream::class, value.javaClass, appendix = supportedStreamsInfo())
            writeEnum(StreamReference.UNSUPPORTED)
            return
        }
        writeEnum(StreamReference.IN)
    }

    override suspend fun ReadContext.decode(): InputStream? {
        if (readEnum<StreamReference>() == StreamReference.IN) {
            return System.`in`
        }
        logUnsupported("deserialize", InputStream::class, appendix = supportedStreamsInfo())
        return null
    }

    private
    fun supportedStreamsInfo(): StructuredMessageBuilder = {
        text(" Only ")
        reference("System.in")
        text(" can be used there.")
    }
}


object OutputStreamCodec : Codec<OutputStream> {
    override suspend fun WriteContext.encode(value: OutputStream) {
        when {
            value === System.out -> writeEnum(StreamReference.OUT)
            value === System.err -> writeEnum(StreamReference.ERR)
            else -> {
                logUnsupported("serialize", OutputStream::class, value.javaClass, appendix = supportedStreamsInfo())
                writeEnum(StreamReference.UNSUPPORTED)
            }
        }
    }

    override suspend fun ReadContext.decode(): OutputStream? {
        return when (readEnum<StreamReference>()) {
            StreamReference.OUT -> System.out
            StreamReference.ERR -> System.err
            else -> {
                logUnsupported("deserialize", OutputStream::class, appendix = supportedStreamsInfo())
                null
            }
        }
    }

    private
    fun supportedStreamsInfo(): StructuredMessageBuilder {
        return {
            text(" Only ")
            reference("System.out")
            text(" or ")
            reference("System.err")
            text(" can be used there.")
        }
    }
}
