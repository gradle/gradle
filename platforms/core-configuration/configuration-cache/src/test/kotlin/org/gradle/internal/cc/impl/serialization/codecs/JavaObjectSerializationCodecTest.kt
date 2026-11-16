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

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableSetMultimap
import com.google.common.reflect.TypeToken
import org.gradle.api.Project
import org.gradle.internal.configuration.problems.DocumentationSection.NotYetImplementedJavaSerialization
import org.gradle.internal.configuration.problems.PropertyKind
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.extensions.stdlib.uncheckedCast
import org.gradle.internal.extensions.stdlib.useToRun
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.sameInstance
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import spock.lang.Issue
import org.mockito.kotlin.mock
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.Externalizable
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.io.ObjectOutput
import java.io.ObjectOutputStream
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import java.time.LocalDate


class JavaObjectSerializationCodecTest : AbstractUserTypeCodecTest() {

    @Test
    fun `can handle mix of Serializable and plain beans`() {

        val bean = Pair(42, "42")

        verifyRoundtripOf({
            // A plain bean that holds a reference to a serializable object
            // sharing a reference to another plain bean.
            Pair(SerializableWriteObjectBean(bean), bean)
        }) { (decodedSerializable, decodedBean) ->

            decodedSerializable.apply {
                assertThat(
                    "defaultWriteObject / defaultReadObject handles non-transient fields",
                    value,
                    equalTo(bean)
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
    }

    @Test
    fun `can handle writeReplace readResolve`() {
        verifyRoundtripOf({ SerializableWriteReplaceBean() }) {
            assertThat(
                it.value,
                equalTo<Any>("42")
            )
        }
    }

    @Test
    fun `unimplemented serialization feature problems link to the Java serialization section`() {
        val exception = assertThrows(UnsupportedOperationException::class.java) {
            try {
                serializationProblemsOf(UnsupportedSerializableBean())
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
        assertThat(
            exception.message,
            containsString(NotYetImplementedJavaSerialization.anchor)
        )
    }

    @Test
    fun `can handle Externalizable beans`() {
        verifyRoundtripOf({ ExternalizableBean().apply { value = 42 } }) {
            assertThat(
                it.value,
                equalTo(42)
            )
        }
    }

    @Test
    fun `can handle Externalizable beans that contain serializable beans`() {
        verifyRoundtripOf({ ExternalizableBeanContainingSerializable(33, 42 to "foo") }) {
            assertThat(
                it.value,
                equalTo(33)
            )
            assertThat(
                it.child,
                equalTo(42 to "foo")
            )
        }
    }

    @Test
    fun `can handle Serializable bean containing Externalizable bean`() {
        verifyRoundtripOf({
            SerializableBeanContainingExternalizable().apply {
                someString = "foo"
                childBean = ExternalizableBean(42)
                someInt = 13
            }
        }) {
            assertThat(it.someString, equalTo("foo"))
            assertThat(
                it.childBean?.value,
                equalTo(42)
            )
            assertThat(it.someInt, equalTo(13))
        }
    }

    @Test
    fun `can handle multiple writeObject implementations in the hierarchy`() {
        verifyRoundtripOf({ MultiWriteObjectBean() }) { bean ->
            assertThat(
                bean.stringValue,
                equalTo("42")
            )
            assertThat(
                bean.intValue,
                equalTo(42)
            )
            assertThat(
                bean.writeOrder,
                equalTo(listOf("superclass", "subclass"))
            )
            assertThat(
                bean.readOrder,
                equalTo(listOf("superclass", "subclass"))
            )
        }
    }

    @Test
    fun `can handle writeObject without readObject`() {
        verifyRoundtripOf({ SerializableWriteObjectOnlyBean() }) {
            assertThat(
                it.value,
                equalTo<Any>("42")
            )
        }
    }

    @Test
    fun `can handle readObject without writeObject`() {
        verifyRoundtripOf({ SerializableReadObjectOnlyBean("42") }) {
            assertThat(
                it.value,
                equalTo<Any>("42")
            )
            assertThat(
                it.transientValue,
                equalTo<Any>("42")
            )
        }
    }

    @Test
    fun `preserves identity of Serializable objects`() {
        verifyRoundtripOf({
            val writeReplaceBean = SerializableWriteReplaceBean()
            val writeObjectBean = SerializableWriteObjectBean(Pair(writeReplaceBean, writeReplaceBean))
            pairOf(writeObjectBean)
        }) { (first, second) ->
            assertThat(
                first,
                sameInstance(second)
            )
            val decodedPair = first.value as Pair<*, *>
            assertThat(
                decodedPair.first,
                sameInstance(decodedPair.second)
            )
        }
    }

    @Test
    fun `preserves identity of Externalizable objects`() {
        verifyRoundtripOf({
            val toShare = ExternalizableBean(42)
            toShare to toShare
        }) { (first, second) ->
            assertThat(first.value, equalTo(42))
            assertThat(first, sameInstance(second))
        }
    }

    @Test
    fun `user types codec leaves bean trace of Serializable objects`() {

        val bean = SerializableWriteObjectBean(mock<Project>())

        val problems = serializationProblemsOf(bean)

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
        verifyRoundtripOf({ pairOf(SerializableReadResolveBean()) }) { (first, second) ->
            assertThat(
                "it preserves identity",
                first,
                sameInstance(second)
            )
            assertThat(
                "it returns readResolve result",
                first.value,
                equalTo("42")
            )
        }
    }

    @Test
    fun `can handle recursive writeReplace`() {
        verifyRoundtripOf({ pairOf(SerializableRecursiveWriteReplaceBean()) }) { (first, second) ->
            assertThat(
                "it preserves identity",
                first,
                sameInstance(second)
            )
            assertThat(
                "it allows writeReplace to initialize the object",
                first.value,
                equalTo("42")
            )
        }
    }

    @Test
    fun `can handle circular writeReplace`() {
        verifyRoundtripOf({ pairOf(TypeToken.of(String::class.java)) }) { (first, second) ->
            assertThat(
                "it preserves identity",
                first,
                sameInstance(second)
            )
            assertThat(
                first,
                equalTo(TypeToken.of(String::class.java))
            )
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/39855")
    fun `can handle writeObject readObject with readResolve`() {
        verifyRoundtripOf({ pairOf(SerializableWriteObjectReadResolveBean("42")) }) { (first, second) ->
            assertThat(
                "it preserves identity",
                first,
                sameInstance(second)
            )
            assertThat(
                "it returns readResolve result",
                first.value,
                equalTo("42")
            )
        }
    }

    @Test
    @Issue("https://github.com/gradle/gradle/issues/39855")
    fun `can handle Guava immutable multimaps`() {
        verifyRoundtripOf({
            Pair(
                ImmutableSetMultimap.of("a", 1, "a", 2, "b", 3),
                ImmutableListMultimap.of("a", 1, "a", 1, "b", 3)
            )
        }) { (setMultimap, listMultimap) ->
            assertThat(
                setMultimap,
                equalTo(ImmutableSetMultimap.of("a", 1, "a", 2, "b", 3))
            )
            assertThat(
                listMultimap,
                equalTo(ImmutableListMultimap.of("a", 1, "a", 1, "b", 3))
            )
        }
    }

    @Test
    fun `can handle writeReplace to Externalizable with readResolve`() {
        verifyRoundtripOf({ LocalDate.of(2026, 11, 16) }) {
            assertThat(
                it,
                equalTo(LocalDate.of(2026, 11, 16))
            )
        }
    }

    @Test
    fun `can handle writeReplace to record with readResolve`() {
        verifyRoundtripOf({ SerializableWriteReplaceToRecordBean("42") }) {
            assertThat(
                it.value,
                equalTo("42")
            )
        }
    }

    @Test
    fun `can handle writeReplace to serialized lambda`() {
        verifyRoundtripOf({ SerializableSupplier { "42" } }) {
            assertThat(
                it.get(),
                equalTo("42")
            )
        }
    }

    @Test
    fun `can handle writeReplace to writeObject with readResolve`() {
        verifyRoundtripOf<Any>({ SerializableWriteReplaceToMultimapBean(ImmutableSetMultimap.of("a", 1)) }) {
            assertThat(
                it,
                equalTo<Any>(ImmutableSetMultimap.of("a", 1))
            )
        }
    }

    @Test
    fun `references read before readResolve see the unresolved object`() {
        verifyRoundtripOf({ CircularReadResolveBean("42").apply { child = CircularReadResolveBean.Child(this) } }) { resolved ->
            assertThat(
                resolved.value,
                equalTo("resolved(42)")
            )
            assertThat(
                "the cycle keeps pointing to the object readResolve was invoked on",
                resolved.child!!.parent.value,
                equalTo("42")
            )
        }
    }

    @Test
    fun `ignores private readResolve of superclass`() {
        verifyRoundtripOf({ PrivateReadResolveSubBean("42") }) {
            assertThat(
                it.value,
                equalTo<Any>("42")
            )
        }
    }

    private
    fun <T> pairOf(bean: T) = bean.let { it to it }

    private
    fun <T : Any> verifyRoundtripOf(factory: () -> T, verify: (T) -> Unit) {
        verify(javaSerializationRoundtripOf(factory()))
        verify(configurationCacheRoundtripOf(factory()))
    }

    private
    fun <T : Any> javaSerializationRoundtripOf(bean: T): T =
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

    class UnsupportedSerializableBean : Serializable {

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            objectOutputStream.putFields() // will throw
        }
    }

    class SerializableBeanContainingExternalizable(
        var someString: String? = null,
        var childBean: ExternalizableBean? = null,
        var someInt: Int? = null
    ) : Serializable

    open class ExternalizableBean(var value: Int = 0) : Externalizable {

        override fun writeExternal(out: ObjectOutput) {
            out.writeInt(value)
        }

        override fun readExternal(`in`: ObjectInput) {
            value = `in`.readInt()
        }
    }

    class ExternalizableBeanContainingSerializable(value: Int = 0, var child: Pair<Int, String>? = null) : ExternalizableBean(value) {
        override fun writeExternal(out: ObjectOutput) {
            super.writeExternal(out)
            out.writeObject(child)
        }

        override fun readExternal(`in`: ObjectInput) {
            value = `in`.readInt()
            child = `in`.readObject().uncheckedCast()
        }
    }

    open class BaseWriteObjectBean(
        var intValue: Int? = null,
        var writeOrder: MutableList<String> = arrayListOf(),
        var readOrder: MutableList<String>? = null
    ) : Serializable {

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            writeOrder.add("superclass")
            objectOutputStream.writeInt(42)
        }

        private
        fun readObject(objectInputStream: ObjectInputStream) {
            markRead("superclass")
            intValue = objectInputStream.readInt()
        }

        protected
        fun markRead(from: String) {
            if (readOrder == null) {
                readOrder = arrayListOf()
            }
            readOrder!!.add(from)
        }
    }

    class MultiWriteObjectBean(var stringValue: String? = null) : BaseWriteObjectBean() {

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            writeOrder.add("subclass")
            objectOutputStream.writeUTF("42")
            objectOutputStream.writeObject(writeOrder)
        }

        private
        fun readObject(objectInputStream: ObjectInputStream) {
            markRead("subclass")
            stringValue = objectInputStream.readUTF()
            writeOrder = objectInputStream.readObject().uncheckedCast()
        }
    }

    class SerializableRecursiveWriteReplaceBean(var value: Any? = null) : Serializable {

        @Suppress("unused")
        private
        fun writeReplace(): Any {
            value = "42"
            return this
        }
    }

    class SerializableReadResolveBean(val value: Any? = null) : Serializable {

        companion object {
            val SINGLETON = SerializableReadResolveBean("42")
        }

        @Suppress("unused")
        fun readResolve(): Any = SINGLETON
    }

    class SerializableWriteReplaceBean(val value: Any? = null) : Serializable {

        @Suppress("unused")
        fun writeReplace(): Any = Memento()

        private
        class Memento : Serializable {
            @Suppress("unused")
            fun readResolve(): Any = SerializableWriteReplaceBean("42")
        }
    }

    /**
     * Follows the pattern used by Guava immutable multimaps since 33.6.0.
     */
    class SerializableWriteObjectReadResolveBean(@Transient val value: String? = null) : Serializable {

        @Transient
        private
        var deserializationReplacement: SerializableWriteObjectReadResolveBean? = null

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            objectOutputStream.defaultWriteObject()
            objectOutputStream.writeUTF(value!!)
        }

        private
        fun readObject(objectInputStream: ObjectInputStream) {
            objectInputStream.defaultReadObject()
            deserializationReplacement = SerializableWriteObjectReadResolveBean(objectInputStream.readUTF())
        }

        @Suppress("unused")
        private
        fun readResolve(): Any = deserializationReplacement!!
    }

    class CircularReadResolveBean(var value: String? = null) : Serializable {

        var child: Child? = null

        class Child(val parent: CircularReadResolveBean) : Serializable

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            objectOutputStream.defaultWriteObject()
        }

        @Suppress("unused")
        private
        fun readResolve(): Any = CircularReadResolveBean("resolved($value)").also { it.child = child }
    }

    fun interface SerializableSupplier : Serializable {
        fun get(): String
    }

    class SerializableWriteReplaceToRecordBean(val value: String) : Serializable {

        @Suppress("unused")
        private
        fun writeReplace(): Any = Memento(value)

        @JvmRecord
        private
        data class Memento(val value: String) : Serializable {
            @Suppress("unused")
            private
            fun readResolve(): Any = SerializableWriteReplaceToRecordBean(value)
        }
    }

    class SerializableWriteReplaceToMultimapBean(val value: Any) : Serializable {

        @Suppress("unused")
        private
        fun writeReplace(): Any = value
    }

    open class PrivateReadResolveBaseBean(val value: Any? = null) : Serializable {

        @Suppress("unused")
        private
        fun readResolve(): Any = PrivateReadResolveBaseBean("base")
    }

    class PrivateReadResolveSubBean(value: Any? = null) : PrivateReadResolveBaseBean(value) {

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            objectOutputStream.defaultWriteObject()
        }
    }

    class SerializableWriteObjectOnlyBean(var value: Any? = null) : Serializable {

        private
        fun writeObject(objectOutputStream: ObjectOutputStream) {
            value = "42"
            objectOutputStream.defaultWriteObject()
        }
    }

    class SerializableReadObjectOnlyBean(var value: Any? = null) : Serializable {

        @Transient
        var transientValue: Any? = null

        @Suppress("UNUSED_PARAMETER")
        private
        fun readObject(objectInputStream: ObjectInputStream) {
            transientValue = "42"
            objectInputStream.defaultReadObject()
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
