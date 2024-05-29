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

package org.gradle.configurationcache.fingerprint

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.gradle.api.Describable
import org.gradle.api.logging.Logger
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.configurationcache.CheckedFingerprint
import org.gradle.internal.configuration.problems.PropertyProblem
import org.gradle.internal.configuration.problems.PropertyTrace
import org.gradle.internal.configuration.problems.StructuredMessageBuilder
import org.gradle.internal.serialize.graph.CircularReferences
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.IsolateOwner
import org.gradle.internal.serialize.graph.ReadContext
import org.gradle.internal.serialize.graph.ReadIdentities
import org.gradle.internal.serialize.graph.ReadIsolate
import org.gradle.internal.serialize.graph.Tracer
import org.gradle.internal.serialize.graph.WriteContext
import org.gradle.internal.serialize.graph.WriteIdentities
import org.gradle.internal.serialize.graph.WriteIsolate
import org.gradle.internal.serialize.graph.BeanStateReader
import org.gradle.internal.serialize.graph.BeanStateWriter
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.graph.runWriteOperation
import org.gradle.internal.Try
import org.gradle.internal.file.FileType
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.io.InputStream
import java.io.OutputStream


class ConfigurationCacheFingerprintCheckerTest {

    @Test
    fun `first modified init script is reported`() {
        assertThat(
            invalidationReasonForInitScriptsChange(
                from = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1),
                    File("unchanged.gradle.kts") to TestHashCodes.hashCodeFrom(1)
                ),
                to = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(2),
                    File("unchanged.gradle.kts") to TestHashCodes.hashCodeFrom(1)
                )
            ),
            equalTo("init script 'init.gradle.kts' has changed")
        )
    }

    @Test
    fun `index of first modified init script is reported when file names differ`() {
        assertThat(
            invalidationReasonForInitScriptsChange(
                from = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1),
                    File("before.gradle.kts") to TestHashCodes.hashCodeFrom(2),
                    File("other.gradle.kts") to TestHashCodes.hashCodeFrom(3)
                ),
                to = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1),
                    File("after.gradle.kts") to TestHashCodes.hashCodeFrom(42),
                    File("other.gradle.kts") to TestHashCodes.hashCodeFrom(3)
                )
            ),
            equalTo("content of 2nd init script, 'after.gradle.kts', has changed")
        )
    }

    @Test
    fun `added init script is reported`() {
        assertThat(
            invalidationReasonForInitScriptsChange(
                from = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1)
                ),
                to = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1),
                    File("added.init.gradle.kts") to TestHashCodes.hashCodeFrom(2)
                )
            ),
            equalTo("init script 'added.init.gradle.kts' has been added")
        )
    }

    @Test
    fun `added init scripts are reported`() {
        assertThat(
            invalidationReasonForInitScriptsChange(
                from = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1)
                ),
                to = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1),
                    File("added.init.gradle.kts") to TestHashCodes.hashCodeFrom(2),
                    File("another.init.gradle.kts") to TestHashCodes.hashCodeFrom(3)
                )
            ),
            equalTo("init script 'added.init.gradle.kts' and 1 more have been added")
        )
    }

    @Test
    fun `removed init script is reported`() {
        assertThat(
            invalidationReasonForInitScriptsChange(
                from = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1),
                    File("removed.init.gradle.kts") to TestHashCodes.hashCodeFrom(2)
                ),
                to = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1)
                )
            ),
            equalTo("init script 'removed.init.gradle.kts' has been removed")
        )
    }

    @Test
    fun `removed init scripts are reported`() {
        assertThat(
            invalidationReasonForInitScriptsChange(
                from = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1),
                    File("removed.init.gradle.kts") to TestHashCodes.hashCodeFrom(2),
                    File("another.init.gradle.kts") to TestHashCodes.hashCodeFrom(3)
                ),
                to = listOf(
                    File("init.gradle.kts") to TestHashCodes.hashCodeFrom(1)
                )
            ),
            equalTo("init script 'removed.init.gradle.kts' and 1 more have been removed")
        )
    }

    @Test
    fun `build script invalidation reason`() {
        val scriptFile = File("build.gradle.kts")
        assertThat(
            checkFingerprintGiven(
                mock {
                    on { hashCodeAndTypeOf(scriptFile) } doReturn (TestHashCodes.hashCodeFrom(1) to FileType.RegularFile)
                    on { displayNameOf(scriptFile) } doReturn "displayNameOf(scriptFile)"
                },
                ConfigurationCacheFingerprint.InputFile(
                    scriptFile,
                    TestHashCodes.hashCodeFrom(2)
                )
            ),
            equalTo("file 'displayNameOf(scriptFile)' has changed")
        )
    }

    @Test
    fun `build input file has been removed`() {
        val inputFile = File("input.txt")
        // no need to match a missing file hash, as long it is changed from the original one
        val missingFileHash = TestHashCodes.hashCodeFrom(2)
        val originalFileHash = TestHashCodes.hashCodeFrom(1)
        assertThat(
            checkFingerprintGiven(
                mock {
                    on { hashCodeAndTypeOf(inputFile) } doReturn (missingFileHash to FileType.Missing)
                    on { displayNameOf(inputFile) } doReturn "displayNameOf(inputFile)"
                },
                ConfigurationCacheFingerprint.InputFile(
                    inputFile,
                    originalFileHash
                )
            ),
            equalTo("file 'displayNameOf(inputFile)' has been removed")
        )
    }

    @Test
    fun `build input file is replaced by directory`() {
        val inputFile = File("input.txt")
        // all we care is that it is changed from the original one
        val newDirectoryHash = TestHashCodes.hashCodeFrom(2)
        val originalFileHash = TestHashCodes.hashCodeFrom(1)
        assertThat(
            checkFingerprintGiven(
                mock {
                    on { hashCodeAndTypeOf(inputFile) } doReturn (newDirectoryHash to FileType.Directory)
                    on { displayNameOf(inputFile) } doReturn "displayNameOf(inputFile)"
                },
                ConfigurationCacheFingerprint.InputFile(
                    inputFile,
                    originalFileHash
                )
            ),
            equalTo("file 'displayNameOf(inputFile)' has been replaced by a directory")
        )
    }

    @Test
    fun `build input file system entry has been removed`() {
        val inputFile = File("input.txt")
        assertThat(
            checkFingerprintGiven(
                mock {
                    on { hashCodeAndTypeOf(inputFile) } doReturn (TestHashCodes.hashCodeFrom(1) to FileType.Missing)
                    on { displayNameOf(inputFile) } doReturn "displayNameOf(inputFile)"
                },
                ConfigurationCacheFingerprint.InputFileSystemEntry(
                    inputFile,
                    FileType.RegularFile
                )
            ),
            equalTo("the file system entry 'displayNameOf(inputFile)' has been removed")
        )
    }

    @Test
    fun `invalidation reason includes ValueSource description`() {

        // given:
        val describableValueSource = mock<ValueSource<Any, ValueSourceParameters>>(
            extraInterfaces = arrayOf(Describable::class)
        ) {
            on { (this as Describable).displayName } doReturn "my value source"
        }

        val obtainedValue = obtainedValueMock()

        // expect:
        assertThat(
            checkFingerprintGiven(
                mock {
                    on { instantiateValueSourceOf(obtainedValue) } doReturn describableValueSource
                },
                ConfigurationCacheFingerprint.ValueSource(obtainedValue)
            ),
            equalTo("my value source has changed")
        )
    }

    private
    fun invalidationReasonForInitScriptsChange(
        from: Iterable<Pair<File, HashCode>>,
        to: List<Pair<File, HashCode>>
    ): InvalidationReason? = to.toMap().let { toMap ->
        checkFingerprintGiven(
            mock {
                on { allInitScripts } doReturn toMap.keys.toList()
                on { hashCodeAndTypeOf(any()) }.then { invocation ->
                    toMap[invocation.getArgument(0)] to FileType.RegularFile
                }
                on { displayNameOf(any()) }.then { invocation ->
                    invocation.getArgument<File>(0).name
                }
            },
            ConfigurationCacheFingerprint.InitScripts(
                from.map { (file, hash) -> ConfigurationCacheFingerprint.InputFile(file, hash) }
            )
        )
    }

    private
    fun checkFingerprintGiven(
        host: ConfigurationCacheFingerprintChecker.Host,
        fingerprint: ConfigurationCacheFingerprint
    ): InvalidationReason? {

        val readContext = recordWritingOf {
            write(fingerprint)
            write(null)
        }

        val checkedFingerprint = readContext.runReadOperation {
            ConfigurationCacheFingerprintChecker(host).run {
                checkBuildScopedFingerprint()
            }
        }
        return when (checkedFingerprint) {
            is CheckedFingerprint.Valid -> null
            is CheckedFingerprint.EntryInvalid -> checkedFingerprint.reason
            else -> throw IllegalArgumentException()
        }
    }

    private
    fun obtainedValueMock(): ObtainedValue = mock {
        on { value } doReturn Try.successful(42)
    }

    private
    fun recordWritingOf(writeOperation: suspend WriteContext.() -> Unit): PlaybackReadContext =
        RecordingWriteContext().apply {
            runWriteOperation(writeOperation)
        }.toReadContext()

    /**
     * A [WriteContext] implementation that avoids serialization altogether
     * and simply records the written values.
     *
     * The written state can then be read from the [ReadContext] returned
     * by [toReadContext].
     */
    private
    class RecordingWriteContext : WriteContext {

        private
        val values = mutableListOf<Any?>()

        fun toReadContext() =
            PlaybackReadContext(values.toList())

        override fun writeSmallInt(value: Int) {
            values.add(value)
        }

        override suspend fun write(value: Any?) {
            values.add(value)
        }

        override val tracer: Tracer?
            get() = null

        override val sharedIdentities: WriteIdentities
            get() = undefined()

        override val isolate: WriteIsolate
            get() = undefined()

        override val circularReferences: CircularReferences
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

        override fun onError(error: Exception, message: StructuredMessageBuilder) =
            undefined()

        override fun push(codec: Codec<Any?>): Unit =
            undefined()

        override fun push(owner: IsolateOwner): Unit =
            undefined()

        override fun push(owner: IsolateOwner, codec: Codec<Any?>): Unit =
            undefined()

        override fun pop(): Unit =
            undefined()

        override suspend fun forIncompatibleType(path: String, action: suspend () -> Unit) =
            undefined()

        override fun writeNullableString(value: CharSequence?): Unit =
            undefined()

        override fun writeNullableSmallInt(value: Int?): Unit =
            undefined()

        override fun writeLong(value: Long): Unit =
            undefined()

        override fun writeShort(value: Short) =
            undefined()

        override fun writeFloat(value: Float) =
            undefined()

        override fun writeDouble(value: Double) =
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

        override fun encodeChunked(writeAction: Encoder.EncodeAction<Encoder>) =
            undefined()

        override fun writeInt(value: Int): Unit =
            undefined()
    }

    private
    class PlaybackReadContext(values: Iterable<Any?>) : ReadContext {

        private
        val reader = values.iterator()

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

        override fun onFinish(action: () -> Unit) =
            undefined()

        override fun <T : Any> getSingletonProperty(propertyType: Class<T>): T =
            undefined()

        override fun beanStateReaderFor(beanType: Class<*>): BeanStateReader =
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

        override fun onError(error: Exception, message: StructuredMessageBuilder) =
            undefined()

        override fun push(codec: Codec<Any?>): Unit =
            undefined()

        override fun push(owner: IsolateOwner): Unit =
            undefined()

        override fun push(owner: IsolateOwner, codec: Codec<Any?>): Unit =
            undefined()

        override fun pop(): Unit =
            undefined()

        override suspend fun forIncompatibleType(path: String, action: suspend () -> Unit) =
            undefined()

        override fun readInt(): Int =
            undefined()

        override fun readNullableSmallInt(): Int? =
            undefined()

        override fun readShort(): Short =
            undefined()

        override fun readFloat(): Float =
            undefined()

        override fun readDouble(): Double =
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

        override fun <T : Any?> decodeChunked(decodeAction: Decoder.DecodeAction<Decoder, T>): T =
            undefined()

        override fun skipChunked() =
            undefined()
    }
}


private
fun undefined(): Nothing =
    TODO("test execution shouldn't get here")
