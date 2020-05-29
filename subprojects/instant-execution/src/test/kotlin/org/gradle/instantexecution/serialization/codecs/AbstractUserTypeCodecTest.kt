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

package org.gradle.instantexecution.serialization.codecs

import com.nhaarman.mockitokotlin2.mock
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.instantexecution.coroutines.runToCompletion
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.problems.ProblemsListener
import org.gradle.instantexecution.problems.PropertyProblem
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.instantexecution.serialization.withIsolate
import org.gradle.internal.io.NullOutputStream
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.util.TestUtil
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.MatcherAssert.assertThat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream


abstract class AbstractUserTypeCodecTest {

    protected
    fun serializationProblemsOf(bean: Any, codec: Codec<Any?>): List<PropertyProblem> =
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
    fun <T : Any> roundtrip(graph: T, codec: Codec<Any?> = userTypesCodec()): T =
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
        writeTo(outputStream, graph, codec)
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
                runToCompletion {
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
                runToCompletion {
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
    fun writeContextFor(encoder: Encoder, codec: Codec<Any?>, problemHandler: ProblemsListener) =
        DefaultWriteContext(
            codec = codec,
            encoder = encoder,
            scopeLookup = mock(),
            logger = mock(),
            problemsListener = problemHandler
        )

    private
    fun readContextFor(inputStream: ByteArrayInputStream, codec: Codec<Any?>) =
        DefaultReadContext(
            codec = codec,
            decoder = KryoBackedDecoder(inputStream),
            instantiatorFactory = TestUtil.instantiatorFactory(),
            constructors = BeanConstructors(TestCrossBuildInMemoryCacheFactory()),
            logger = mock(),
            problemsListener = mock()
        )

    protected
    fun userTypesCodec() = codecs().userTypesCodec

    protected
    fun codecs() = Codecs(
        directoryFileTreeFactory = mock(),
        fileCollectionFactory = mock(),
        fileLookup = mock(),
        propertyFactory = mock(),
        filePropertyFactory = mock(),
        fileResolver = mock(),
        instantiator = mock(),
        listenerManager = mock(),
        projectStateRegistry = mock(),
        taskNodeFactory = mock(),
        fingerprinterRegistry = mock(),
        buildOperationExecutor = mock(),
        classLoaderHierarchyHasher = mock(),
        isolatableFactory = mock(),
        valueSnapshotter = mock(),
        buildServiceRegistry = mock(),
        managedFactoryRegistry = mock(),
        parameterScheme = mock(),
        actionScheme = mock(),
        attributesFactory = mock(),
        transformListener = mock(),
        valueSourceProviderFactory = mock(),
        patternSetFactory = mock(),
        fileOperations = mock(),
        fileSystem = mock(),
        fileFactory = mock()
    )
}
