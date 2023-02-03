/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.cache.internal.btree

import org.gradle.internal.serialize.BaseSerializerFactory
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import spock.lang.Specification

class KeyHasherTest extends Specification {
    def "can reuse to hash more than one key"() {
        def hasher = new KeyHasher(BaseSerializerFactory.LONG_SERIALIZER)

        expect:
        hasher.getHashCode(12L) != hasher.getHashCode(11L)
        hasher.getHashCode(12L) == hasher.getHashCode(12L)
        hasher.getHashCode(12L) == new KeyHasher(BaseSerializerFactory.LONG_SERIALIZER).getHashCode(12L)
    }

    def "can reuse to hash large key"() {
        def hasher = new KeyHasher(new InefficientSerializer())

        expect:
        hasher.getHashCode(12000L) != hasher.getHashCode(12001L)
        hasher.getHashCode(12000L) == hasher.getHashCode(12000L)
        hasher.getHashCode(12000L) == new KeyHasher(new InefficientSerializer()).getHashCode(12000L)
    }

    static class InefficientSerializer implements Serializer<Long> {
        @Override
        void write(Encoder encoder, Long value) throws Exception {
            value.times { int n ->
                encoder.writeInt(n)
            }
        }

        @Override
        Long read(Decoder decoder) throws EOFException, Exception {
            throw new UnsupportedOperationException()
        }
    }
}
