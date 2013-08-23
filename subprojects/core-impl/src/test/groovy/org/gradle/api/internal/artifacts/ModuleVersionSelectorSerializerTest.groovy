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

package org.gradle.api.internal.artifacts

import org.gradle.messaging.serialize.InputStreamBackedDecoder
import org.gradle.messaging.serialize.OutputStreamBackedEncoder
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class ModuleVersionSelectorSerializerTest extends Specification {
    private serializer = new ModuleVersionSelectorSerializer()

    def "serializes"() {
        def bytes = new ByteArrayOutputStream()
        def encoder = new OutputStreamBackedEncoder(bytes)

        when:
        serializer.write(encoder, newSelector("org", "foo", "5.0"))
        def result = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(bytes.toByteArray())))

        then:
        result == newSelector("org", "foo", "5.0")
    }
}
