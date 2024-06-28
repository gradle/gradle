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
import org.gradle.internal.cc.impl.io.ByteBufferPool
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
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.infra.Blackhole
import java.io.OutputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.zip.GZIPOutputStream


@State(Scope.Benchmark)
open class CCStoreBenchmark {

    private
    lateinit var graph: Any

    @Setup
    fun setup() {
        graph = (1..1024).map { Peano.fromInt(1024) }
    }

    @Benchmark
    fun withSnappyCompression(bh: Blackhole) {
        writeTo(
            KryoBackedEncoder(
                compressorOutputStreamFor(
                    BlackholeOutputStream(bh)
                )
            ),
            graph
        )
    }

    @Benchmark
    fun withGZIPCompression(bh: Blackhole) {
        writeTo(
            KryoBackedEncoder(
                GZIPOutputStream(
                    BlackholeOutputStream(bh)
                )
            ),
            graph
        )
    }

    @Benchmark
    fun withParallelSnappyCompression(bh: Blackhole) {
        writeTo(
            KryoBackedEncoder(
                ParallelOutputStream.of {
                    compressorOutputStreamFor(
                        BlackholeOutputStream(bh)
                    )
                },
                ParallelOutputStream.recommendedBufferSize
            ),
            graph
        )
    }

    @Benchmark
    fun withParallelSnappyCompressionAndArrayBlockingQueue(bh: Blackhole) {
        writeTo(
            KryoBackedEncoder(
                ParallelOutputStream.of(ArrayBlockingQueue(ByteBufferPool.maxChunks)) {
                    compressorOutputStreamFor(
                        BlackholeOutputStream(bh)
                    )
                },
                ParallelOutputStream.recommendedBufferSize
            ),
            graph
        )
    }

    @Benchmark
    fun withParallelGZIPCompression(bh: Blackhole) {
        writeTo(
            KryoBackedEncoder(
                ParallelOutputStream.of {
                    GZIPOutputStream(
                        BlackholeOutputStream(bh)
                    )
                },
                ParallelOutputStream.recommendedBufferSize
            ),
            graph
        )
    }

    @Benchmark
    fun withoutCompression(bh: Blackhole) {
        writeTo(
            KryoBackedEncoder(BlackholeOutputStream(bh)),
            graph
        )
    }

    private
    fun compressorOutputStreamFor(outputStream: OutputStream) =
        FramedSnappyCompressorOutputStream(
            outputStream,
            SnappyCompressorOutputStream
                .createParameterBuilder(SnappyCompressorInputStream.DEFAULT_BLOCK_SIZE)
                .tunedForSpeed()
                .build()
        )

    internal
    class BlackholeOutputStream(val bh: Blackhole) : OutputStream() {
        override fun write(b: Int) {
            bh.consume(b)
        }
    }

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

    internal
    sealed class Peano {

        companion object {

            fun fromInt(n: Int): Peano = (0 until n).fold(Z as Peano) { acc, _ -> S(acc) }
        }

        fun toInt(): Int = sequence().count() - 1

        object Z : Peano() {
            override fun toString() = "Z"
        }

        data class S(val n: Peano) : Peano() {
            override fun toString() = "S($n)"
        }

        private
        fun sequence() = generateSequence(this) { previous ->
            when (previous) {
                is Z -> null
                is S -> previous.n
            }
        }
    }
}
