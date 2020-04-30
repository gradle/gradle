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
import org.gradle.instantexecution.problems.PropertyKind
import org.gradle.instantexecution.problems.PropertyTrace
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
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
}
