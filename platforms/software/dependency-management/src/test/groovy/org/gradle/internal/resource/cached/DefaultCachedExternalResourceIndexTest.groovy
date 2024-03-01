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

package org.gradle.internal.resource.cached

import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class DefaultCachedExternalResourceIndexTest extends Specification {

    @Rule TestNameTestDirectoryProvider folder = new TestNameTestDirectoryProvider(getClass())

    def commonPath = folder.createDir("common").toPath()

    def "value serializer can relativize path"() {
        given:
        def serializer = new DefaultCachedExternalResourceIndex.CachedExternalResourceSerializer(commonPath)
        def encoder = Mock(Encoder)
        def value = Mock(CachedExternalResource)
        def fileName = "file.txt"

        when:
        serializer.write(encoder, value)

        then:
        _ * value.getCachedFile() >> commonPath.resolve(fileName).toFile()

        1 * encoder.writeString(fileName)
    }

    def "value serializer can expand relative path"() {
        given:
        def serializer = new DefaultCachedExternalResourceIndex.CachedExternalResourceSerializer(commonPath)
        def decoder = Mock(Decoder)
        def fileName = "file.txt"

        when:
        def result = serializer.read(decoder)

        then:
        1 * decoder.readBoolean() >> true
        1 * decoder.readString() >> fileName
        1 * decoder.readLong() >> 42L
        1 * decoder.readBoolean() >> false

        result.getCachedFile() == commonPath.resolve(fileName).toFile()
    }
}
