/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.types

import org.gradle.test.fixtures.concurrent.ConcurrentSpec

class ExternalPluginsInstrumentationTypeRegistryTest extends ConcurrentSpec {

    def "should collect instrumented types"() {
        given:
        def gradleCoreRegistry = new TestGradleCoreInstrumentationTypeRegistry([
            "org/gradle/api/DefaultTask": ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set,
            "org/gradle/api/tasks/DefaultVerificationTask": ["org/gradle/api/tasks/VerificationTask"] as Set
        ])
        def directSuperTypes = [
            "B": ["org/gradle/api/DefaultTask"] as Set,
            "C": ["org/gradle/api/tasks/DefaultVerificationTask", "B"] as Set,
            "D": ["C"] as Set,
            "E": ["D"] as Set,
            "F": ["E"] as Set
        ] as Map<String, Set<String>>
        def typeRegistry = new ExternalPluginsInstrumentationTypeRegistry(directSuperTypes, gradleCoreRegistry)

        when:
        def instrumentedSuperTypes = typeRegistry.getSuperTypes("F")

        then:
        instrumentedSuperTypes ==~ [
            "org/gradle/api/Task",
            "org/gradle/api/internal/TaskInternal",
            "org/gradle/api/DefaultTask",
            "org/gradle/api/tasks/VerificationTask",
            "org/gradle/api/tasks/DefaultVerificationTask"
        ].collect { it.toString() }
    }

    def "should collect instrumented types for types cycles no matter the order of queries"() {
        given:
        def gradleCoreRegistry = new TestGradleCoreInstrumentationTypeRegistry([:])
        def directSuperTypes = [
            "D": ["G", "H", "org/gradle/api/internal/TaskInternal"] as Set,
            "F": ["D"] as Set,
            "G": ["F", "org/gradle/api/Task"] as Set
        ] as Map<String, Set<String>>

        when:
        def typeRegistry = new ExternalPluginsInstrumentationTypeRegistry(directSuperTypes, gradleCoreRegistry)
        def gInstrumentedSuperTypes = typeRegistry.getSuperTypes("G")
        def fInstrumentedSuperTypes = typeRegistry.getSuperTypes("F")
        def dInstrumentedSuperTypes = typeRegistry.getSuperTypes("D")

        then:
        gInstrumentedSuperTypes ==~ ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set<String>
        dInstrumentedSuperTypes ==~ ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set<String>
        fInstrumentedSuperTypes ==~ ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set<String>

        when:
        typeRegistry = new ExternalPluginsInstrumentationTypeRegistry(directSuperTypes, gradleCoreRegistry)
        dInstrumentedSuperTypes = typeRegistry.getSuperTypes("D")
        fInstrumentedSuperTypes = typeRegistry.getSuperTypes("F")
        gInstrumentedSuperTypes = typeRegistry.getSuperTypes("G")

        then:
        gInstrumentedSuperTypes ==~ ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set<String>
        dInstrumentedSuperTypes ==~ ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set<String>
        fInstrumentedSuperTypes ==~ ["org/gradle/api/Task", "org/gradle/api/internal/TaskInternal"] as Set<String>
    }

    private static class TestGradleCoreInstrumentationTypeRegistry implements InstrumentationTypeRegistry {

        private final Map<String, Set<String>> instrumentedSuperTypes

        TestGradleCoreInstrumentationTypeRegistry(Map<String, Set<String>> instrumentedSuperTypes) {
            this.instrumentedSuperTypes = instrumentedSuperTypes
        }

        @Override
        Set<String> getSuperTypes(String type) {
            return instrumentedSuperTypes.getOrDefault(type, Collections.emptySet())
        }

        @Override
        boolean isEmpty() {
            return instrumentedSuperTypes.isEmpty()
        }
    }
}
