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
package org.gradle.api.reporting.dependencies.internal

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newDependency
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newUnresolvedDependency
/**
 * Unit tests for <code>StrictDependencyResultSpec</code>
 */
class StrictDependencyResultSpecTest extends Specification {

    def "knows matching dependencies"() {
        expect:
        new StrictDependencyResultSpec(moduleIdentifier).isSatisfiedBy(newDependency("org.foo", "foo-core", "1.0"))

        where:
        moduleIdentifier << [id('org.foo', 'foo-core')]
    }

    def "knows mismatching dependencies"() {
        expect:
        !new StrictDependencyResultSpec(moduleIdentifier).isSatisfiedBy(newDependency("org.foo", "foo-core", "1.0"))

        where:
        moduleIdentifier << [id('org.foobar', 'foo-core'),
                             id('org.foo', 'foo-coreImpl')]
    }

    def "matches unresolved dependencies"() {
        expect:
        new StrictDependencyResultSpec(moduleIdentifier).isSatisfiedBy(newUnresolvedDependency("org.foo", "foo-core", "5.0"))

        where:
        moduleIdentifier << [id('org.foo', 'foo-core')]
    }

    def "does not match unresolved dependencies"() {
        expect:
        !new StrictDependencyResultSpec(moduleIdentifier).isSatisfiedBy(newUnresolvedDependency("org.foo", "foo-core", "5.0"))

        where:
        moduleIdentifier << [id('org.foobar', 'foo-core'),
                             id('org.foo', 'foo-coreImpl')]
    }

    private DefaultModuleIdentifier id(String group, String name) {
        DefaultModuleIdentifier.newId(group, name)
    }
}
