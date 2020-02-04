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

package org.gradle.instantexecution.fingerprint

import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.Describable
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.instantexecution.runToCompletion
import org.gradle.instantexecution.serialization.Codec
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.PropertyProblem
import org.gradle.instantexecution.serialization.PropertyTrace
import org.gradle.instantexecution.serialization.ReadContext
import org.gradle.instantexecution.serialization.ReadIdentities
import org.gradle.instantexecution.serialization.ReadIsolate
import org.gradle.instantexecution.serialization.WriteContext
import org.gradle.instantexecution.serialization.WriteIdentities
import org.gradle.instantexecution.serialization.WriteIsolate
import org.gradle.instantexecution.serialization.beans.BeanStateReader
import org.gradle.instantexecution.serialization.beans.BeanStateWriter
import org.gradle.internal.Try
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.jetbrains.kotlin.backend.common.push
import org.junit.Test
import java.io.InputStream
import java.io.OutputStream


class InstantExecutionFingerprintCheckerTest {

    @Test
    fun `invalidation reason includes ValueSource description`() {

        // given:
        val describableValueSource = mock<ValueSource<Any, ValueSourceParameters>>(
            extraInterfaces = arrayOf(Describable::class)
        ) {
            on { (this as Describable).displayName } doReturn "my value source"
        }

        val obtainedValue = mock<ObtainedValue> {
            on { value } doReturn Try.successful<Any>(42)
        }

        val fingerprintCheckerHost = mock<InstantExecutionFingerprintChecker.Host> {
            on { instantiateValueSourceOf(obtainedValue) } doReturn describableValueSource
        }

        // when:
        val readContext = recordWritingOf {
            InstantExecutionFingerprintChecker.FingerprintEncoder.run {
                encode(
                    InstantExecutionCacheFingerprint(
                        inputFiles = emptyList(),
                        obtainedValues = listOf(obtainedValue)
                    )
                )
            }
        }

        // and:
        val invalidationReason = readContext.readToCompletion {
            InstantExecutionFingerprintChecker(fingerprintCheckerHost).run {
                checkFingerprint()
            }
        }

        // then:
        assertThat(
            invalidationReason,
            equalTo("my value source has changed")
        )
    }

    private
    fun recordWritingOf(writeOperation: suspend WriteContext.() -> Unit): PlaybackReadContext =
        RecordingWriteContext().apply {
            runToCompletion { writeOperation() }
        }.toReadContext()

    /**
     * A [WriteContext] implementation that avoids serialization altogether
     * and simply records the written values.
     *
     * The written state can then be read from the [ReadContext] returned
     * by [toReadContext].
     */
    class RecordingWriteContext : WriteContext {

        private
        val values = mutableListOf<Any?>()

        fun toReadContext() =
            PlaybackReadContext(values.toList())

        override fun writeSmallInt(value: Int) {
            values.push(value)
        }

        override suspend fun write(value: Any?) {
            values.push(value)
        }

        override val sharedIdentities: WriteIdentities
            get() = undefined()

        override val isolate: WriteIsolate
            get() = undefined()

        override fun beanStateWriterFor(beanType: Class<*>): BeanStateWriter =
            undefined()

        override fun writeClass(type: Class<*>): Unit =
            undefined()

        override val logger: Logger
            get() = undefined()

        override var trace: PropertyTrace
            get() = undefined()
            set(_) {}

        override fun onProblem(problem: PropertyProblem): Unit =
            undefined()

        override fun push(codec: Codec<Any?>): Unit =
            undefined()

        override fun push(owner: IsolateOwner, codec: Codec<Any?>): Unit =
            undefined()

        override fun pop(): Unit =
            undefined()

        override fun writeNullableString(value: CharSequence?): Unit =
            undefined()

        override fun writeLong(value: Long): Unit =
            undefined()

        override fun writeString(value: CharSequence?): Unit =
            undefined()

        override fun writeBytes(bytes: ByteArray?): Unit =
            undefined()

        override fun writeBytes(bytes: ByteArray?, offset: Int, count: Int): Unit =
            undefined()

        override fun writeByte(value: Byte): Unit =
            undefined()

        override fun writeBinary(bytes: ByteArray?): Unit =
            undefined()

        override fun writeBinary(bytes: ByteArray?, offset: Int, count: Int): Unit =
            undefined()

        override fun writeSmallLong(value: Long): Unit =
            undefined()

        override fun writeBoolean(value: Boolean): Unit =
            undefined()

        override fun getOutputStream(): OutputStream =
            undefined()

        override fun writeInt(value: Int): Unit =
            undefined()
    }

    class PlaybackReadContext(values: Iterable<Any?>) : ReadContext {

        private
        val reader = values.iterator()

        fun <T> readToCompletion(readOperation: suspend ReadContext.() -> T): T =
            runToCompletion { readOperation() }

        override fun readSmallInt(): Int = next()

        override suspend fun read(): Any? = next()

        @Suppress("unchecked_cast")
        private
        fun <T : Any?> next(): T = reader.next() as T

        override val sharedIdentities: ReadIdentities
            get() = undefined()

        override val isolate: ReadIsolate
            get() = undefined()

        override val classLoader: ClassLoader
            get() = undefined()

        override fun beanStateReaderFor(beanType: Class<*>): BeanStateReader =
            undefined()

        override fun getProject(path: String): ProjectInternal =
            undefined()

        override var immediateMode: Boolean
            get() = undefined()
            set(_) {}

        override fun readClass(): Class<*> =
            undefined()

        override val logger: Logger
            get() = undefined()

        override var trace: PropertyTrace
            get() = undefined()
            set(_) {}

        override fun onProblem(problem: PropertyProblem): Unit =
            undefined()

        override fun push(codec: Codec<Any?>): Unit =
            undefined()

        override fun push(owner: IsolateOwner, codec: Codec<Any?>): Unit =
            undefined()

        override fun pop(): Unit =
            undefined()

        override fun readInt(): Int =
            undefined()

        override fun readNullableString(): String? =
            undefined()

        override fun readByte(): Byte =
            undefined()

        override fun readBinary(): ByteArray =
            undefined()

        override fun skipBytes(count: Long): Unit =
            undefined()

        override fun readLong(): Long =
            undefined()

        override fun readSmallLong(): Long =
            undefined()

        override fun getInputStream(): InputStream =
            undefined()

        override fun readString(): String =
            undefined()

        override fun readBoolean(): Boolean =
            undefined()

        override fun readBytes(buffer: ByteArray?): Unit =
            undefined()

        override fun readBytes(buffer: ByteArray?, offset: Int, count: Int): Unit =
            undefined()
    }
}


private
fun undefined(): Nothing =
    TODO("test execution shouldn't get here")
