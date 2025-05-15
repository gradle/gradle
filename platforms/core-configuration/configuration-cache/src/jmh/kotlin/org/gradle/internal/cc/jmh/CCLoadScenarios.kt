/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.cc.jmh

import com.nhaarman.mockitokotlin2.mock
import org.apache.commons.compress.compressors.snappy.FramedSnappyCompressorInputStream
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.cc.impl.serialize.DefaultClassDecoder
import org.gradle.internal.cc.jmh.CCStoreScenarios.compressorOutputStreamFor
import org.gradle.internal.cc.jmh.CCStoreScenarios.writeTo
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.beans.services.BeanConstructors
import org.gradle.internal.serialize.beans.services.DefaultBeanStateReaderLookup
import org.gradle.internal.serialize.graph.BeanStateReaderLookup
import org.gradle.internal.serialize.graph.Codec
import org.gradle.internal.serialize.graph.DefaultReadContext
import org.gradle.internal.serialize.graph.runReadOperation
import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder
import org.gradle.util.TestUtil
import org.gradle.util.internal.SupportedEncryptionAlgorithm
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.spec.SecretKeySpec


internal
object CCLoadScenarios {

    class State(
        val uncompressed: ByteArray,
        val snappy: ByteArray,
        val gzip: ByteArray,
    )

    fun createState(graph: Any): State = State(
        uncompressed = writeUncompressed(graph),
        snappy = writeWithSnappy(graph),
        gzip = writeWithGZIP(graph)
    )

    fun writeWithGZIP(graph: Any) =
        writeToByteArrayWith(graph) {
            KryoBackedEncoder(
                GZIPOutputStream(
                    encrypted(it),
                )
            )
        }

    fun readWithGZIP(bytes: ByteArray) =
        readFrom(GZIPInputStream(decrypted(bytes)))

    fun writeWithSnappy(graph: Any) =
        writeToByteArrayWith(graph) {
            KryoBackedEncoder(
                compressorOutputStreamFor(
                    encrypted(it)
                )
            )
        }

    fun readWithSnappy(bytes: ByteArray) =
        readFrom(FramedSnappyCompressorInputStream(decrypted(bytes)))

    fun writeUncompressed(graph: Any) =
        writeToByteArrayWith(graph) {
            KryoBackedEncoder(encrypted(it))
        }

    fun readUncompressed(bytes: ByteArray) =
        readFrom(decrypted(bytes))

    private
    fun writeToByteArrayWith(graph: Any, encoder: (OutputStream) -> Encoder): ByteArray =
        ByteArrayOutputStream().let { bos ->
            writeTo(encoder(bos), graph)
            bos.toByteArray()
        }

    private
    fun encrypted(it: OutputStream): OutputStream =
        encryption.encryptedStream(it, key)

    private
    fun decrypted(uncompressed: ByteArray): InputStream =
        encryption.decryptedStream(ByteArrayInputStream(uncompressed), key)

    private
    val encryption = SupportedEncryptionAlgorithm.getDefault()

    private
    val key = SecretKeySpec("0123456789ABCDEF".toByteArray(), encryption.algorithm)

    private
    fun readFrom(inputStream: InputStream, codec: Codec<Any?> = userTypesCodec()): Any? =
        readContextFor(inputStream, codec).run {
            withIsolateMock(codec) {
                runReadOperation {
                    read()
                }
            }
        }

    private
    fun readContextFor(inputStream: InputStream, codec: Codec<Any?>) =
        DefaultReadContext(
            codec = codec,
            decoder = KryoBackedDecoder(inputStream),
            beanStateReaderLookup = beanStateReaderLookupForTesting(),
            logger = mock(),
            problemsListener = mock(),
            classDecoder = DefaultClassDecoder(mock(), mock()),
            isIntegrityCheckEnabled = false
        )

    private
    fun beanStateReaderLookupForTesting(): BeanStateReaderLookup =
        DefaultBeanStateReaderLookup(
            BeanConstructors(TestCrossBuildInMemoryCacheFactory()),
            TestUtil.instantiatorFactory()
        )
}
