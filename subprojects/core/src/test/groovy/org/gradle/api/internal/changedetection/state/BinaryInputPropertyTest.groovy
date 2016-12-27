/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import spock.lang.Specification

import static org.apache.commons.lang.SerializationUtils.deserialize
import static org.apache.commons.lang.SerializationUtils.serialize

class BinaryInputPropertyTest extends Specification {
    def "type with same hash are equal after deserialization"() {
        def current = BinaryInputProperty.create("foo")

        when:
        def previous = deserialize(serialize(current))

        then:
        current == previous
    }
}
