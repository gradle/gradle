/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.operations

import spock.lang.Specification

class ProgressDisplayNameTest extends Specification {

    def "has a text and no owner"() {
        when:
        def name = ProgressDisplayName.of("Running test A")

        then:
        name.text == "Running test A"
        name.owner == null
    }

    def "has a text and the owner"() {
        when:
        def name = ProgressDisplayName.of(":lib", "Building task graph")

        then:
        name.text == "Building task graph"
        name.owner == ":lib"
    }

    def "is equal to a name with the same text and owner"() {
        expect:
        ProgressDisplayName.of(":lib", "Building task graph") == ProgressDisplayName.of(":lib", "Building task graph")
        ProgressDisplayName.of(":lib", "Building task graph").hashCode() == ProgressDisplayName.of(":lib", "Building task graph").hashCode()
        ProgressDisplayName.of("Running test A") == ProgressDisplayName.of("Running test A")
        ProgressDisplayName.of(":lib", "Building task graph") != ProgressDisplayName.of(":other", "Building task graph")
        ProgressDisplayName.of(":lib", "Building task graph") != ProgressDisplayName.of(":lib", "Running test A")
        ProgressDisplayName.of(":lib", "Building task graph") != ProgressDisplayName.of("Building task graph")
    }
}
