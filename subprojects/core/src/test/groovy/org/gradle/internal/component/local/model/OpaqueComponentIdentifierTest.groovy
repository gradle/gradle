/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.internal.component.local.model

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal
import spock.lang.Specification

import static org.gradle.util.Matchers.strictlyEquals

class OpaqueComponentIdentifierTest extends Specification {
    def "is instantiated with non-null constructor parameter value"() {
        when:
        ComponentIdentifier componentIdentifier = new OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation.GRADLE_API)

        then:
        componentIdentifier.displayName == "Gradle API"
        componentIdentifier.toString() == "Gradle API"
    }

    def "is instantiated with null constructor parameter value"() {
        when:
        new OpaqueComponentIdentifier(null)

        then:
        thrown(AssertionError)
    }

    def "can compare with equivalent identifiers"() {
        expect:
        ComponentIdentifier componentIdentifier1 = new OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation.GRADLE_API)
        ComponentIdentifier componentIdentifier2 = new OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation.GRADLE_API)
        strictlyEquals(componentIdentifier1, componentIdentifier2)
        componentIdentifier1.hashCode() == componentIdentifier2.hashCode()
        componentIdentifier1.toString() == componentIdentifier2.toString()
    }

    def "can compare with different identifiers"() {
        expect:
        ComponentIdentifier componentIdentifier1 = new OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation.GRADLE_API)
        ComponentIdentifier componentIdentifier2 = new OpaqueComponentIdentifier(DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY)
        !strictlyEquals(componentIdentifier1, componentIdentifier2)
        componentIdentifier1.hashCode() != componentIdentifier2.hashCode()
        componentIdentifier1.toString() != componentIdentifier2.toString()
    }
}
