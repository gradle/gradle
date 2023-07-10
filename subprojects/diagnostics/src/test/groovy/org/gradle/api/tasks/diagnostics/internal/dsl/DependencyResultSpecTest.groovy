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

package org.gradle.api.tasks.diagnostics.internal.dsl

import org.gradle.internal.component.local.model.TestComponentIdentifiers
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newDependency
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newUnresolvedDependency

class DependencyResultSpecTest extends Specification {

    def "knows matching dependencies"() {
        expect:
        new DependencyResultSpec(notation).isSatisfiedBy(newDependency("org.foo", "foo-core", "1.0"))

        where:
        notation << ['org', 'org.foo', 'foo-core', '1.0', 'org.foo:foo-core', 'org.foo:foo-core:1.0']
    }

    def "knows mismatching dependencies"() {
        expect:
        !new DependencyResultSpec(notation).isSatisfiedBy(newDependency("org.foo", "foo-core", "1.0"))

        where:
        notation << ['org1', '2.0', 'core-foo']
    }

    def "matches unresolved dependencies"() {
        expect:
        new DependencyResultSpec(notation).isSatisfiedBy(newUnresolvedDependency("org.foo", "foo-core", "5.0"))

        where:
        notation << ['core', 'foo-core', 'foo-core:5.0', 'org.foo:foo-core:5.0', '5.0']
    }

    def "does not match unresolved dependencies"() {
        expect:
        !new DependencyResultSpec(notation).isSatisfiedBy(newUnresolvedDependency("org.foo", "foo-core", "5.0"))

        where:
        notation << ['xxx', '4.0']
    }

    def "matches by selected module or requested dependency"() {
        expect:
        new DependencyResultSpec(notation).isSatisfiedBy(newDependency("org.foo", "foo-core", "1.+", "1.22"))

        where:
        notation << ['1.+', '1.22', 'foo-core:1.+', 'foo-core:1.22', 'org.foo:foo-core:1.+', 'org.foo:foo-core:1.22']
    }

    def "does not match for dependencies other than requested ModuleComponentSelector"() {
        expect:
        !new DependencyResultSpec(notation).isSatisfiedBy(newDependency(TestComponentIdentifiers.newSelector(":myPath"), "org.foo", "foo-core", "1.22"))

        where:
        notation << ['1.+']
    }
}
