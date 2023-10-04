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

package org.gradle.configurationcache.serialization.codecs

import com.nhaarman.mockitokotlin2.mock
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.configurationcache.extensions.uncheckedCast
import org.gradle.configurationcache.extensions.useToRun
import org.gradle.configurationcache.problems.ProblemsListener
import org.gradle.configurationcache.problems.PropertyProblem
import org.gradle.configurationcache.serialization.Codec
import org.gradle.configurationcache.serialization.DefaultReadContext
import org.gradle.configurationcache.serialization.DefaultWriteContext
import org.gradle.configurationcache.serialization.IsolateOwner
import org.gradle.configurationcache.serialization.MutableIsolateContext
import org.gradle.configurationcache.serialization.beans.BeanConstructors
import org.gradle.configurationcache.serialization.beans.BeanStateReaderLookup
import org.gradle.configurationcache.serialization.beans.BeanStateWriterLookup
import org.gradle.configurationcache.serialization.codecs.jos.JavaSerializationEncodingLookup
import org.gradle.configurationcache.serialization.runReadOperation
import org.gradle.configurationcache.serialization.runWriteOperation
import org.gradle.configurationcache.serialization.withIsolate
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.serialize.FlushableEncoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.TestUtil
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
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
                object : ProblemsListener {
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

    private
    fun writeToByteArray(graph: Any, codec: Codec<Any?>): ByteArray {
        val outputStream = ByteArrayOutputStream()
        writeTo(
            outputStream, graph, codec,
            object : ProblemsListener {
                override fun onProblem(problem: PropertyProblem) {
                    println(problem)
                }
            }
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

    private
    fun readFromByteArray(bytes: ByteArray, codec: Codec<Any?>) =
        readFrom(ByteArrayInputStream(bytes), codec)

    private
    fun readFrom(inputStream: ByteArrayInputStream, codec: Codec<Any?>) =
        readContextFor(inputStream, codec).run {
            initClassLoader(javaClass.classLoader)
            withIsolateMock(codec) {
                runReadOperation {
                    read()
                }
            }
        }

    private
    inline fun <R> MutableIsolateContext.withIsolateMock(codec: Codec<Any?>, block: () -> R): R =
        withIsolate(IsolateOwner.OwnerGradle(mock()), codec) {
            block()
        }

    private
    fun writeContextFor(encoder: FlushableEncoder, codec: Codec<Any?>, problemHandler: ProblemsListener) =
        DefaultWriteContext(
            codec = codec,
            encoder = encoder,
            scopeLookup = mock(),
            beanStateWriterLookup = BeanStateWriterLookup(),
            logger = mock(),
            tracer = null,
            problemsListener = problemHandler
        )

    private
    fun readContextFor(inputStream: ByteArrayInputStream, codec: Codec<Any?>) =
        DefaultReadContext(
            codec = codec,
            decoder = KryoBackedDecoder(inputStream),
            beanStateReaderLookup = BeanStateReaderLookup(BeanConstructors(TestCrossBuildInMemoryCacheFactory()), TestUtil.instantiatorFactory()),
            logger = mock(),
            problemsListener = mock()
        )

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
        objectFactory = mock(),
        instantiator = mock(),
        fileSystemOperations = mock(),
        taskNodeFactory = mock(),
        ordinalGroupFactory = mock(),
        inputFingerprinter = mock(),
        buildOperationExecutor = mock(),
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
