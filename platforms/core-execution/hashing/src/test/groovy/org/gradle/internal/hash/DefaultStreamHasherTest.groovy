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

package org.gradle.internal.hash

import spock.lang.Specification

class DefaultStreamHasherTest extends Specification {
    def "can hash input"() {
        def input = new ByteArrayInputStream("hello".bytes)
        when:
        def hash = new DefaultStreamHasher().hash(input)
        then:
        hash.toString() == "af67a92e9f73b51572e9b94e343d2840"
    }

    def "can hash input while copying it"() {
        def input = new ByteArrayInputStream("hello".bytes)
        def output = new ByteArrayOutputStream()
        when:
        def hash = new DefaultStreamHasher().hashCopy(input, output)
        then:
        hash.toString() == "af67a92e9f73b51572e9b94e343d2840"
        output.toByteArray() == "hello".bytes
    }
}
