/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.internal.serialize.kryo

import org.gradle.internal.serialize.AbstractCodecTest
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.util.internal.EncryptionAlgorithm
import org.gradle.util.internal.SupportedEncryptionAlgorithm

import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

abstract class AbstractEncryptingKryoBackedCodecTest extends AbstractCodecTest {
    private EncryptionAlgorithm encryptionAlgorithm = chooseEncryptionAlgorithm()
    private SecretKey key = KeyGenerator.getInstance(encryptionAlgorithm.getAlgorithm()).generateKey()

    def "strings are deduplicated using #algorithm"() {
        expect:
        def bytes = encode { Encoder encoder ->
            encoder.writeString(new String("one"))
            encoder.writeString(new String("two"))
            encoder.writeString(new String("one"))
            encoder.writeString(new String("two"))
        }
        decode(bytes) { Decoder decoder ->
            def first = decoder.readString()
            def second = decoder.readString()
            def third = decoder.readString()
            def fourth = decoder.readString()
            assert first == "one"
            assert second == "two"
            assert first === third
            assert second === fourth
        }
    }

    @Override
    protected Encoder createEncoder(OutputStream outputStream) {
        new EncryptingEncoder(outputStream, encryptionAlgorithm.newSession(key))
    }

    @Override
    protected Decoder createDecoder(InputStream inputStream) {
        new DecryptingDecoder(inputStream, encryptionAlgorithm.newSession(key))
    }

    abstract def EncryptionAlgorithm chooseEncryptionAlgorithm()
}

class EncryptingWithAESCBCCodecTest extends AbstractEncryptingKryoBackedCodecTest {
    @Override
    EncryptionAlgorithm chooseEncryptionAlgorithm() {
        return SupportedEncryptionAlgorithm.AES_CBC_PADDING
    }
}

class EncryptingWithAESECBCodecTest extends AbstractEncryptingKryoBackedCodecTest {
    @Override
    EncryptionAlgorithm chooseEncryptionAlgorithm() {
        return SupportedEncryptionAlgorithm.AES_ECB_PADDING
    }
}
