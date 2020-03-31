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

import org.gradle.instantexecution.coroutines.runToCompletion
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.MutableIsolateContext
import org.gradle.instantexecution.serialization.PropertyKind
import org.gradle.instantexecution.serialization.PropertyProblem
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.beans.BeanConstructors
import org.gradle.instantexecution.serialization.withIsolate

import org.gradle.internal.io.NullOutputStream

import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.kotlin.dsl.support.useToRun
import org.gradle.util.TestUtil

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
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
            decodedSerializable.transientShort,
            equalTo(SerializableWriteObjectBean.EXPECTED_SHORT)
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
            decodedSerializable.transientFloat,
            equalTo(SerializableWriteObjectBean.EXPECTED_FLOAT)
        )

        assertThat(
            decodedSerializable.transientDouble,
            equalTo(SerializableWriteObjectBean.EXPECTED_DOUBLE)
        )

        assertThat(
            "preserves identities across protocols",
            decodedSerializable.value,
            sameInstance(decodedBean)
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
    fun `can handle Serializable with only writeObject`() {
        assertThat(
            roundtrip(SerializableWriteObjectOnlyBean()).value,
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
    fun `user types codec leaves bean trace of Serializable objects`() {

        val bean = SerializableWriteObjectBean(mock<Project>())

        val problems = serializationProblemsOf(bean, userTypesCodec())

        val fieldTrace = assertInstanceOf<PropertyTrace.Property>(problems.single().trace)
        assertThat(
            fieldTrace.kind,
            equalTo(PropertyKind.Field)
        )
        assertThat(
            fieldTrace.name,
            equalTo("value")
        )

        val beanTrace = assertInstanceOf<PropertyTrace.Bean>(fieldTrace.trace)
        assertThat(
            beanTrace.type,
            sameInstance(bean.javaClass)
        )
    }

    @Test
    fun `internal types codec leaves not implemented trace for unsupported types`() {

        val unsupportedBean = 42 to "42"
        val problems = serializationProblemsOf(unsupportedBean, codecs().internalTypesCodec)
        val problem = problems.single()
        assertInstanceOf<PropertyTrace.Gradle>(
            problem.trace
        )
        assertThat(
            problem.message.toString(),
            equalTo("objects of type 'kotlin.Pair' are not yet supported with instant execution.")
        )
    }

    @Test
    fun `can handle anonymous enum subtypes`() {
        EnumSuperType.values().forEach {
            assertThat(
                roundtrip(it),
                sameInstance(it)
            )
        }
    }

    enum class EnumSuperType {

        SubType1 {
            override fun displayName() = "one"
        },
        SubType2 {
            override fun displayName() = "two"
        };

        abstract fun displayName(): String
    }

    private
    inline fun <reified T> assertInstanceOf(any: Any): T {
        assertThat(any, instanceOf(T::class.java))
        return any.uncheckedCast()
    }

    private
    fun serializationProblemsOf(bean: Any, codec: Codec<Any?>): List<PropertyProblem> =
        mutableListOf<PropertyProblem>().also { problems ->
            writeTo(
                NullOutputStream.INSTANCE,
                bean,
                codec
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

    class SerializableWriteObjectOnlyBean(var value: Any? = null) : Serializable {

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            value = "42"
            objectOutputStream.defaultWriteObject()
        }
    }

    class SerializableWriteObjectBean(val value: Any) : Serializable {

        companion object {

            const val EXPECTED_SHORT: Short = Short.MAX_VALUE

            const val EXPECTED_INT: Int = 42

            const val EXPECTED_STRING: String = "42"

            const val EXPECTED_FLOAT: Float = 1.618f

            const val EXPECTED_DOUBLE: Double = Math.PI
        }

        @Transient
        var transientShort: Short? = null

        @Transient
        var transientInt: Int? = null

        @Transient
        var transientString: String? = null

        @Transient
        var transientFloat: Float? = null

        @Transient
        var transientDouble: Double? = null

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            objectOutputStream.run {
                defaultWriteObject()
                writeShort(EXPECTED_SHORT.toInt())
                writeInt(EXPECTED_INT)
                writeUTF(EXPECTED_STRING)
                writeFloat(EXPECTED_FLOAT)
                writeDouble(EXPECTED_DOUBLE)
            }
        }

        private
        fun readObject(objectInputStream: ObjectInputStream) {
            objectInputStream.defaultReadObject()
            transientShort = objectInputStream.readShort()
            transientInt = objectInputStream.readInt()
            transientString = objectInputStream.readUTF()
            transientFloat = objectInputStream.readFloat()
            transientDouble = objectInputStream.readDouble()
        }
    }

    private
    fun <T : Any> roundtrip(graph: T, codec: Codec<Any?> = userTypesCodec()): T =
        writeToByteArray(graph, codec)
            .let { readFromByteArray(it, codec)!! }
            .uncheckedCast()

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
        problemHandler: (PropertyProblem) -> Unit = mock()
    ) {
        writeContextFor(KryoBackedEncoder(outputStream), codec, problemHandler).useToRun {
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
    fun writeContextFor(encoder: Encoder, codec: Codec<Any?>, problemHandler: (PropertyProblem) -> Unit) =
        DefaultWriteContext(
            codec = codec,
            encoder = encoder,
            scopeLookup = mock(),
            logger = mock(),
            problemHandler = problemHandler
        )

    private
    fun readContextFor(inputStream: ByteArrayInputStream, codec: Codec<Any?>) =
        DefaultReadContext(
            codec = codec,
            decoder = KryoBackedDecoder(inputStream),
            instantiatorFactory = TestUtil.instantiatorFactory(),
            constructors = BeanConstructors(TestCrossBuildInMemoryCacheFactory()),
            logger = mock()
        )

    private
    fun userTypesCodec() = codecs().userTypesCodec

    private
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
        projectFinder = mock(),
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

    @Test
    fun `Peano sanity check`() {

        assertThat(
            Peano.fromInt(0),
            equalTo(Peano.Z)
        )

        assertThat(
            Peano.fromInt(1024).toInt(),
            equalTo(1024)
        )
    }

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
