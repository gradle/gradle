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

package org.gradle.internal.cc.jmh

import com.nhaarman.mockitokotlin2.mock
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorOutputStream
import org.gradle.internal.cc.impl.serialize.DefaultClassEncoder
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.beans.services.DefaultBeanStateWriterLookup
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.openjdk.jmh.infra.Blackhole
import java.io.OutputStream
import java.util.zip.GZIPOutputStream


internal
object CCStoreScenarios {

    internal
    fun withSnappyCompression(bh: Blackhole, graph: Any) {
        writeTo(
            KryoBackedEncoder(
                compressorOutputStreamFor(
                    BlackholeOutputStream(bh)
                )
            ),
            graph
        )
    }

    internal
    fun withGZIPCompression(bh: Blackhole, graph: Any) {
        writeTo(
            KryoBackedEncoder(
                GZIPOutputStream(
                    BlackholeOutputStream(bh)
                )
            ),
            graph
        )
    }

    internal
    fun withoutCompression(bh: Blackhole, graph: Any) {
        writeTo(
            KryoBackedEncoder(BlackholeOutputStream(bh)),
            graph
        )
    }

    internal
    fun compressorOutputStreamFor(outputStream: OutputStream) =
        FramedSnappyCompressorOutputStream(
            outputStream,
//            SnappyCompressorOutputStream.createParameterBuilder(SnappyCompressorInputStream.DEFAULT_BLOCK_SIZE)
//                .tunedForSpeed()
//                .build()
        )

    internal
    fun writeTo(
        encoder: Encoder,
        graph: Any,
        codec: Codec<Any?> = userTypesCodec(),
        problemsListener: ProblemsListener = mock(),
    ) {
        writeContextFor(encoder, codec, problemsListener).useToRun {
            withIsolateMock(codec) {
                runWriteOperation {
                    write(graph)
                }
            }
        }
    }

    private
    fun writeContextFor(encoder: Encoder, codec: Codec<Any?>, problemHandler: ProblemsListener) =
        DefaultWriteContext(
            codec = codec,
            encoder = encoder,
            classEncoder = DefaultClassEncoder(mock()),
            beanStateWriterLookup = DefaultBeanStateWriterLookup(),
            logger = mock(),
            tracer = null,
            problemsListener = problemHandler,
            isIntegrityCheckEnabled = false,
        )
}
