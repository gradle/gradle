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

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.TestComponentIdentifiers
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

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

    DefaultResolvedDependencyResult newDependency(String group='a', String module='a', String version='1', String selectedVersion='1') {
        new DefaultResolvedDependencyResult(newSelector(group, module, version), false, newModule(), newModule(group, module, selectedVersion), Mock(ResolvedVariantResult))
    }

    DefaultUnresolvedDependencyResult newUnresolvedDependency(String group='x', String module='x', String version='1', String selectedVersion='1') {
        def requested = newSelector(group, module, version)
        org.gradle.internal.Factory<String> broken = { "broken" }
        new DefaultUnresolvedDependencyResult(requested, newModule(group, module, selectedVersion), false, new ModuleVersionResolveException(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, module), version), broken), ComponentSelectionReasons.requested())
    }

    DefaultResolvedDependencyResult newDependency(ComponentSelector componentSelector, String group='a', String module='a', String selectedVersion='1') {
        new DefaultResolvedDependencyResult(componentSelector, false, newModule(), newModule(group, module, selectedVersion), Mock(ResolvedVariantResult))
    }

    private ResolvedComponentResult newModule(String group = "a", String module = "a", String version = "1") {
        Mock(ResolvedComponentResult) {
            getModuleVersion() >> DefaultModuleVersionIdentifier.newId(group, module, version)
        }
    }

    static ModuleComponentSelector newSelector(String group, String module, String version) {
        DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, module), version)
    }

}
