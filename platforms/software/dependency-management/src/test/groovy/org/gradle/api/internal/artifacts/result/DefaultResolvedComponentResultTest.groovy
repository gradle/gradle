/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.result

import spock.lang.Specification

import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.*

class DefaultResolvedComponentResultTest extends Specification {

    def "mutating dependencies or dependents is harmless"() {
        given:
        def module = newModule("a", "c", "1")
        def dependency  = newDependency("a", "x", "1")
        def dependent   = newDependency("a", "x2", "1")

        when:
        module.addDependency(dependency)
        module.addDependent(dependent)

        then:
        module.dependencies == [dependency] as Set
        module.dependents   == [dependent] as Set

        when:
        module.dependencies << newDependency("a", "y", "1")
        then:
        thrown(UnsupportedOperationException)

        when:
        module.dependents <<   newDependency("a", "y2", "1")
        then:
        thrown(UnsupportedOperationException)
    }

    def "includes unresolved dependencies"() {
        given:
        def module = newModule()
        def dependency = newDependency()
        def unresolved = newUnresolvedDependency()

        when:
        module.addDependency(dependency)
        module.addDependency(unresolved)

        then:
        module.dependencies == [dependency, unresolved] as Set
    }

    def "associating the same result to a variant multiple times does not cause duplicate results"() {
        given:
        def module = newModule()
        def dependency = newDependency()
        def variant = module.getVariant(1)

        when:
        module.addDependency(dependency)

        module.associateDependencyToVariant(dependency, variant)
        module.associateDependencyToVariant(dependency, variant)

        then:
        module.getDependenciesForVariant(variant) == [dependency]
    }
}
