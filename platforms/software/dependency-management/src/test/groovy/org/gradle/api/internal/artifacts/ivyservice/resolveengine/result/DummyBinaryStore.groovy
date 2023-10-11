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
package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.cache.internal.BinaryStore
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder

class DummyBinaryStore implements BinaryStore {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream()
    private Encoder output = new OutputStreamBackedEncoder(bytes)

    void write(WriteAction write) {
        write.write(output)
    }

    BinaryData done() {
        new BinaryData() {
            Decoder decoder
            def <T> T read(ReadAction<T> readAction) {
                if (decoder == null) {
                    decoder = new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray()))
                }
                readAction.read(decoder)
            }

            void close() {
                decoder = null
            }
        }
    }
}
