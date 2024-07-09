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
import org.apache.commons.compress.compressors.snappy.SnappyCompressorInputStream
import org.apache.commons.compress.compressors.snappy.SnappyCompressorOutputStream
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.impl.io.ParallelOutputStream
import org.gradle.internal.cc.impl.serialize.Codecs
import org.gradle.internal.cc.impl.serialize.DefaultClassEncoder
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.serialize.FlushableEncoder
import org.gradle.internal.serialize.beans.services.DefaultBeanStateWriterLookup
import org.gradle.internal.serialize.codecs.core.jos.JavaSerializationEncodingLookup
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.MutableIsolateContext
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.openjdk.jmh.infra.Blackhole
import java.io.OutputStream
import java.nio.ByteBuffer
import java.util.Queue
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
    fun withParallelSnappyCompression(queue: Queue<ByteBuffer>, bh: Blackhole, graph: Any) {
        writeTo(
            KryoBackedEncoder(
                ParallelOutputStream.of(queue) {
                    compressorOutputStreamFor(
                        BlackholeOutputStream(bh)
                    )
                },
                ParallelOutputStream.recommendedBufferSize
            ),
            graph
        )
    }

    internal
    fun withParallelGZIPCompression(bh: Blackhole, graph: Any) {
        writeTo(
            KryoBackedEncoder(
                ParallelOutputStream.of {
                    GZIPOutputStream(
                        BlackholeOutputStream(bh),
                        ParallelOutputStream.recommendedBufferSize
                    )
                },
                ParallelOutputStream.recommendedBufferSize
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

    private
    fun compressorOutputStreamFor(outputStream: OutputStream) =
        FramedSnappyCompressorOutputStream(
            outputStream,
            SnappyCompressorOutputStream.createParameterBuilder(SnappyCompressorInputStream.DEFAULT_BLOCK_SIZE)
                .tunedForSpeed()
                .build()
        )

    private
    fun writeTo(
        encoder: KryoBackedEncoder,
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
    fun writeContextFor(encoder: FlushableEncoder, codec: Codec<Any?>, problemHandler: ProblemsListener) =
        DefaultWriteContext(
            codec = codec,
            encoder = encoder,
            classEncoder = DefaultClassEncoder(mock()),
            beanStateWriterLookup = DefaultBeanStateWriterLookup(),
            logger = mock(),
            tracer = null,
            problemsListener = problemHandler
        )

    private
    inline fun <R> MutableIsolateContext.withIsolateMock(codec: Codec<Any?>, block: () -> R): R =
        withIsolate(IsolateOwners.OwnerGradle(mock()), codec) {
            block()
        }

    private
    fun userTypesCodec() = codecs().userTypesCodec()

    private
    fun codecs() = Codecs(
        directoryFileTreeFactory = mock(),
        fileCollectionFactory = mock(),
        artifactSetConverter = mock(),
        fileLookup = mock(),
        propertyFactory = mock(),
        filePropertyFactory = mock(),
        fileResolver = mock(),
        objectFactory = mock(),
        instantiator = mock(),
        fileSystemOperations = mock(),
        taskNodeFactory = mock(),
        ordinalGroupFactory = mock(),
        inputFingerprinter = mock(),
        buildOperationRunner = mock(),
        classLoaderHierarchyHasher = mock(),
        isolatableFactory = mock(),
        managedFactoryRegistry = mock(),
        parameterScheme = mock(),
        actionScheme = mock(),
        attributesFactory = mock(),
        valueSourceProviderFactory = mock(),
        calculatedValueContainerFactory = mock(),
        patternSetFactory = mock(),
        fileOperations = mock(),
        fileFactory = mock(),
        includedTaskGraph = mock(),
        buildStateRegistry = mock(),
        documentationRegistry = mock(),
        javaSerializationEncodingLookup = JavaSerializationEncodingLookup(),
        flowProviders = mock(),
        transformStepNodeFactory = mock(),
    )
}
