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
import org.gradle.api.Project
import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.problems.PropertyKind
import org.gradle.instantexecution.problems.PropertyTrace
import org.gradle.kotlin.dsl.support.useToRun
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable


class SerializableBeanCodecTest : AbstractUserTypeCodecTest() {

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

        decodedSerializable.apply {
            assertThat(
                "defaultWriteObject / defaultReadObject handles non-transient fields",
                value,
                equalTo(serializable.value)
            )

            assertThat(
                transientByte,
                equalTo(SerializableWriteObjectBean.EXPECTED_BYTE)
            )

            assertThat(
                transientShort,
                equalTo(SerializableWriteObjectBean.EXPECTED_SHORT)
            )

            assertThat(
                transientInt,
                equalTo(SerializableWriteObjectBean.EXPECTED_INT)
            )

            assertThat(
                transientLong,
                equalTo(SerializableWriteObjectBean.EXPECTED_LONG)
            )

            assertThat(
                transientString,
                equalTo(SerializableWriteObjectBean.EXPECTED_STRING)
            )

            assertThat(
                transientFloat,
                equalTo(SerializableWriteObjectBean.EXPECTED_FLOAT)
            )

            assertThat(
                transientDouble,
                equalTo(SerializableWriteObjectBean.EXPECTED_DOUBLE)
            )

            assertThat(
                "preserves identities across protocols",
                value,
                sameInstance(decodedBean)
            )
        }
    }

    @Test
    fun `can handle writeReplace readResolve`() {
        assertThat(
            roundtrip(SerializableWriteReplaceBean()).value,
            equalTo<Any>("42")
        )
    }

    @Test
    fun `can handle writeObject without readObject`() {
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
    fun `can handle readResolve without writeReplace`() {
        // ensure we got the java serialization behaviour right
        verifyReadResolveBehaviourOf(::javaSerializationRoundtrip)
        verifyReadResolveBehaviourOf(::roundtrip)
    }

    private
    fun verifyReadResolveBehaviourOf(roundtrip: (Any) -> Any) {
        val (first, second) = roundtrip(pairOf(SerializableReadResolveBean()))
            .uncheckedCast<Pair<SerializableReadResolveBean, SerializableReadResolveBean>>()
        assertThat(
            "it preserves identity",
            first,
            sameInstance(second)
        )
        assertThat(
            "it returns readResolve result",
            first.value,
            equalTo<Any>("42")
        )
    }

    @Test
    fun `can handle recursive writeReplace`() {
        // ensure we got the java serialization behaviour right
        verifyRecursiveWriteReplaceBehaviourOf(::javaSerializationRoundtrip)
        verifyRecursiveWriteReplaceBehaviourOf(::roundtrip)
    }

    private
    fun verifyRecursiveWriteReplaceBehaviourOf(roundtrip: (Any) -> Any) {
        val (first, second) = roundtrip(pairOf(SerializableRecursiveWriteReplaceBean()))
            .uncheckedCast<Pair<SerializableRecursiveWriteReplaceBean, SerializableRecursiveWriteReplaceBean>>()
        assertThat(
            "it preserves identity",
            first,
            sameInstance(second)
        )
        assertThat(
            "it allows writeReplace to initialize the object",
            first.value,
            equalTo<Any>("42")
        )
    }

    private
    fun <T> pairOf(bean: T) = bean.let { it to it }

    private
    fun <T : Any> javaSerializationRoundtrip(bean: T): T =
        javaDeserialize(javaSerialize(bean)).uncheckedCast()

    private
    fun <T : Any> javaSerialize(bean: T): ByteArray =
        ByteArrayOutputStream().let { bytes ->
            ObjectOutputStream(bytes).useToRun {
                writeObject(bean)
            }
            bytes.toByteArray()
        }

    private
    fun javaDeserialize(bytes: ByteArray) =
        ObjectInputStream(ByteArrayInputStream(bytes)).readObject()

    class SerializableRecursiveWriteReplaceBean(var value: Any? = null) : Serializable {

        @Suppress("unused")
        private
        fun writeReplace(): Any? {
            value = "42"
            return this
        }
    }

    class SerializableReadResolveBean(val value: Any? = null) : Serializable {

        companion object {
            val SINGLETON = SerializableReadResolveBean("42")
        }

        @Suppress("unused")
        private
        fun readResolve(): Any? = SINGLETON
    }

    class SerializableWriteReplaceBean(val value: Any? = null) : Serializable {

        @Suppress("unused")
        private
        fun writeReplace() = Memento()

        private
        class Memento {
            @Suppress("unused")
            private
            fun readResolve(): Any? = SerializableWriteReplaceBean("42")
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

            const val EXPECTED_BYTE: Byte = 42

            const val EXPECTED_INT: Int = 42

            const val EXPECTED_LONG: Long = 42L

            const val EXPECTED_STRING: String = "42"

            const val EXPECTED_FLOAT: Float = 1.618f

            const val EXPECTED_DOUBLE: Double = Math.PI
        }

        @Transient
        var transientByte: Byte? = null

        @Transient
        var transientShort: Short? = null

        @Transient
        var transientInt: Int? = null

        @Transient
        var transientLong: Long? = null

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
                writeByte(EXPECTED_BYTE.toInt())
                writeShort(EXPECTED_SHORT.toInt())
                writeInt(EXPECTED_INT)
                writeLong(EXPECTED_LONG)
                writeUTF(EXPECTED_STRING)
                writeFloat(EXPECTED_FLOAT)
                writeDouble(EXPECTED_DOUBLE)
            }
        }

        private
        fun readObject(objectInputStream: ObjectInputStream) {
            objectInputStream.defaultReadObject()
            transientByte = objectInputStream.readByte()
            transientShort = objectInputStream.readShort()
            transientInt = objectInputStream.readInt()
            transientLong = objectInputStream.readLong()
            transientString = objectInputStream.readUTF()
            transientFloat = objectInputStream.readFloat()
            transientDouble = objectInputStream.readDouble()
        }
    }
}
