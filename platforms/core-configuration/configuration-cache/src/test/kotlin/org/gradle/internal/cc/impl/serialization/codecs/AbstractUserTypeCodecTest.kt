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

package org.gradle.internal.cc.impl.serialization.codecs

import org.gradle.internal.cc.base.problems.AbstractProblemsListener
import org.gradle.internal.cc.base.serialize.IsolateOwners
import org.gradle.internal.cc.impl.serialize.Codecs
import org.gradle.internal.cc.impl.serialize.DefaultClassDecoder
import org.gradle.internal.cc.impl.serialize.DefaultClassEncoder
import org.gradle.internal.configuration.problems.ProblemsListener
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.extensions.stdlib.useToRun
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.serialize.FlushableEncoder
import org.gradle.internal.serialize.beans.services.DefaultBeanStateWriterLookup
import org.gradle.internal.serialize.beans.services.test.beanStateReaderLookupForTesting
import org.gradle.internal.serialize.codecs.core.jos.JavaSerializationEncodingLookup
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.DefaultReadContext
import org.gradle.internal.serialize.graph.DefaultWriteContext
import org.gradle.internal.serialize.graph.MutableIsolateContext
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.serialize.graph.withIsolate
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream


abstract class AbstractUserTypeCodecTest {

    protected
    fun serializationProblemsOf(bean: Any, codec: Codec<Any?> = userTypesCodec()): List<PropertyProblem> =
        mutableListOf<PropertyProblem>().also { problems ->
            writeTo(
                NullOutputStream.INSTANCE,
                bean,
                codec,
                object : AbstractProblemsListener() {
                    override fun onProblem(problem: PropertyProblem) {
                        problems += problem
                    }
                }
            )
        }

    protected
    fun <T : Any> configurationCacheRoundtripOf(graph: T, codec: Codec<Any?> = userTypesCodec()): T =
        writeToByteArray(graph, codec)
            .let { readFromByteArray(it, codec)!! }
            .uncheckedCast()

    internal
    inline fun <reified T> assertInstanceOf(any: Any): T {
        assertThat(any, instanceOf(T::class.java))
        return any.uncheckedCast()
    }

    internal
    fun writeToByteArray(graph: Any, codec: Codec<Any?>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        writeTo(
            outputStream, graph, codec,
            loggingProblemsListener()
        )
        return outputStream.toByteArray()
    }

    private
    fun writeTo(
        outputStream: OutputStream,
        graph: Any,
        codec: Codec<Any?>,
        problemsListener: ProblemsListener = mock()
    ) {
        writeContextFor(KryoBackedEncoder(outputStream), codec, problemsListener).useToRun {
            withIsolateMock(codec) {
                runWriteOperation {
                    write(graph)
                }
            }
        }
    }

    internal
    fun readFromByteArray(bytes: ByteArray, codec: Codec<Any?>) =
        readFrom(ByteArrayInputStream(bytes), codec, loggingProblemsListener())

    private
    fun readFrom(inputStream: ByteArrayInputStream, codec: Codec<Any?>, problemsListener: ProblemsListener) =
        readContextFor(inputStream, codec, problemsListener).run {
            withIsolateMock(codec) {
                runReadOperation {
                    read()
                }
            }
        }

    private
    inline fun <R> MutableIsolateContext.withIsolateMock(codec: Codec<Any?>, block: () -> R): R =
        withIsolate(IsolateOwners.OwnerGradle(mock()), codec) {
            block()
        }

    private
    fun writeContextFor(encoder: FlushableEncoder, codec: Codec<Any?>, problemHandler: ProblemsListener) =
        DefaultWriteContext(
            codec = codec,
            encoder = encoder,
            classEncoder = DefaultClassEncoder(mock()),
            beanStateWriterLookup = DefaultBeanStateWriterLookup(),
            isIntegrityCheckEnabled = false,
            logger = mock(),
            tracer = null,
            problemsListener = problemHandler
        )

    private
    fun readContextFor(inputStream: ByteArrayInputStream, codec: Codec<Any?>, problemsListener: ProblemsListener) =
        DefaultReadContext(
            codec = codec,
            decoder = KryoBackedDecoder(inputStream),
            beanStateReaderLookup = beanStateReaderLookupForTesting(),
            isIntegrityCheckEnabled = false,
            logger = mock(),
            problemsListener = problemsListener,
            classDecoder = DefaultClassDecoder(mock(), mock())
        )

    private fun loggingProblemsListener() = object : AbstractProblemsListener() {
        override fun onProblem(problem: PropertyProblem) {
            println(problem)
        }
    }

    private
    fun userTypesCodec() = codecs().userTypesCodec()

    internal
    fun codecs() = Codecs(
        directoryFileTreeFactory = mock(),
        fileCollectionFactory = mock(),
        artifactSetConverter = mock(),
        fileLookup = mock(),
        propertyFactory = mock(),
        filePropertyFactory = mock(),
        fileResolver = mock(),
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
        problems = mock(),
        attributeDesugaring = mock(),
    )
}
