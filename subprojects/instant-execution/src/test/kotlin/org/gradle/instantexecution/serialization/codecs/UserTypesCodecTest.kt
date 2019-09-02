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

import com.nhaarman.mockitokotlin2.mock

import org.gradle.api.Project
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory

import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.runToCompletion
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.PropertyProblem
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.instantexecution.serialization.withIsolate

import org.gradle.internal.io.NullOutputStream

import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable


class UserTypesCodecTest {

    @Test
    fun `can handle deeply nested graphs`() {

        val deepGraph = Peano.fromInt(1024)

        val read = roundtrip(deepGraph)

        assertThat(
            read.toInt(),
            equalTo(deepGraph.toInt())
        )
    }

    @Test
    fun `can handle mix of Serializable and plain beans`() {

        val bean = Pair(42, "42")

        /**
         * A [Serializable] object that holds a reference to a plain bean.
         **/
        val serializable = SerializableWriteObjectBean(bean)

        /**
         * A plain bean that holds a reference to a serializable object
         * sharing a reference to another plain bean.
         **/
        val beanGraph = Pair(serializable, bean)

        val decodedGraph = roundtrip(beanGraph)

        val (decodedSerializable, decodedBean) = decodedGraph

        assertThat(
            "defaultWriteObject / defaultReadObject handles non-transient fields",
            decodedSerializable.value,
            equalTo(serializable.value)
        )

        assertThat(
            decodedSerializable.transientInt,
            equalTo(SerializableWriteObjectBean.EXPECTED_INT)
        )

        assertThat(
            decodedSerializable.transientString,
            equalTo(SerializableWriteObjectBean.EXPECTED_STRING)
        )

        assertThat(
            "preserves identities across protocols",
            decodedSerializable.value,
            sameInstance<Any>(decodedBean)
        )
    }

    @Test
    fun `can handle Serializable writeReplace readResolve`() {
        assertThat(
            roundtrip(SerializableWriteReplaceBean()).value,
            equalTo<Any>("42")
        )
    }

    @Test
    fun `preserves identity of Serializable objects`() {
        val writeReplaceBean = SerializableWriteReplaceBean()
        val writeObjectBean = SerializableWriteObjectBean(Pair(writeReplaceBean, writeReplaceBean))
        val graph = Pair(writeObjectBean, writeObjectBean)
        val decodedGraph = roundtrip(graph)
        assertThat(
            decodedGraph.first,
            sameInstance(decodedGraph.second)
        )
        val decodedPair = decodedGraph.first.value as Pair<*, *>
        assertThat(
            decodedPair.first,
            sameInstance(decodedPair.second)
        )
    }

    @Test
    fun `leaves bean trace of Serializable objects`() {

        val bean = SerializableWriteObjectBean(mock<Project>())

        val problems = serializationProblemsOf(bean)

        assertThat(
            problems.single().trace.toString(),
            equalTo(
                "field `value` of `${bean.javaClass.name}` bean found in unknown property"
            )
        )
    }

    private
    fun serializationProblemsOf(bean: Any): List<PropertyProblem> =
        mutableListOf<PropertyProblem>().also { problems ->
            writeTo(
                NullOutputStream.INSTANCE,
                bean
            ) { problems += it }
        }

    class SerializableWriteReplaceBean(val value: Any? = null) : Serializable {

        @Suppress("unused")
        private
        fun writeReplace() = Memento()

        private
        class Memento {
            @Suppress("unused")
            fun readResolve() = SerializableWriteReplaceBean("42")
        }
    }

    class SerializableWriteObjectBean(val value: Any) : Serializable {

        companion object {

            const val EXPECTED_INT: Int = 42

            const val EXPECTED_STRING: String = "42"
        }

        @Transient
        var transientInt: Int? = null

        @Transient
        var transientString: String? = null

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            objectOutputStream.run {
                defaultWriteObject()
                writeInt(EXPECTED_INT)
                writeUTF(EXPECTED_STRING)
            }
        }

        private
        fun readObject(objectInputStream: ObjectInputStream) {
            objectInputStream.defaultReadObject()
            transientInt = objectInputStream.readInt()
            transientString = objectInputStream.readUTF()
        }
    }

    private
    fun <T : Any> roundtrip(graph: T): T =
        writeToByteArray(graph)
            .let(::readFromByteArray)!!
            .uncheckedCast()

    private
    fun writeToByteArray(graph: Any): ByteArray {
        val outputStream = ByteArrayOutputStream()
        writeTo(outputStream, graph)
        return outputStream.toByteArray()
    }

    private
    fun writeTo(
        outputStream: OutputStream,
        graph: Any,
        problemHandler: (PropertyProblem) -> Unit = mock()
    ) {
        KryoBackedEncoder(outputStream).use { encoder ->
            writeContextFor(encoder, problemHandler).run {
                withIsolateMock {
                    runToCompletion {
                        write(graph)
                    }
                }
            }
        }
    }

    private
    fun readFromByteArray(bytes: ByteArray) =
        readFrom(ByteArrayInputStream(bytes))

    private
    fun readFrom(inputStream: ByteArrayInputStream) =
        readContextFor(inputStream).run {
            initClassLoader(javaClass.classLoader)
            withIsolateMock {
                runToCompletion {
                    read()
                }
            }
        }

    private
    inline fun <R> MutableIsolateContext.withIsolateMock(block: () -> R): R =
        withIsolate(IsolateOwner.OwnerGradle(mock()), codecs().userTypesCodec) {
            block()
        }

    private
    fun writeContextFor(encoder: Encoder, problemHandler: (PropertyProblem) -> Unit) =
        DefaultWriteContext(
            codec = codecs().userTypesCodec,
            encoder = encoder,
            scopeLookup = mock(),
            logger = mock(),
            problemHandler = problemHandler
        )

    private
    fun readContextFor(inputStream: ByteArrayInputStream) =
        DefaultReadContext(
            codec = codecs().userTypesCodec,
            decoder = KryoBackedDecoder(inputStream),
            constructors = BeanConstructors(TestCrossBuildInMemoryCacheFactory()),
            logger = mock()
        )

    private
    fun codecs() = Codecs(
        directoryFileTreeFactory = mock(),
        fileCollectionFactory = mock(),
        fileLookup = mock(),
        filePropertyFactory = mock(),
        fileResolver = mock(),
        instantiator = mock(),
        listenerManager = mock(),
        projectStateRegistry = mock(),
        taskNodeFactory = mock(),
        fingerprinterRegistry = mock(),
        classLoaderHierarchyHasher = mock(),
        buildOperationExecutor = mock(),
        isolatableFactory = mock(),
        valueSnapshotter = mock(),
        fileCollectionFingerprinterRegistry = mock(),
        isolatableSerializerRegistry = mock(),
        projectFinder = mock(),
        parameterScheme = mock(),
        actionScheme = mock(),
        attributesFactory = mock(),
        transformListener = mock()
    )

    @Test
    fun `Peano sanity check`() {

        assertThat(
            Peano.fromInt(0),
            equalTo<Peano>(Peano.Zero)
        )

        assertThat(
            Peano.fromInt(1024).toInt(),
            equalTo(1024)
        )
    }

    sealed class Peano {

        companion object {

            fun fromInt(n: Int): Peano = (0 until n).fold(Zero as Peano) { acc, _ -> Succ(acc) }
        }

        fun toInt(): Int = sequence().count() - 1

        object Zero : Peano()

        data class Succ(val n: Peano) : Peano()

        private
        fun sequence() = generateSequence(this) { previous ->
            when (previous) {
                is Zero -> null
                is Succ -> previous.n
            }
        }
    }
}
