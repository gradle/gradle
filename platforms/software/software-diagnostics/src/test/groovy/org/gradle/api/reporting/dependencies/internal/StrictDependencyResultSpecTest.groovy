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


import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedVariantResult
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasons
import org.gradle.api.internal.artifacts.result.DefaultResolvedDependencyResult
import org.gradle.api.internal.artifacts.result.DefaultUnresolvedDependencyResult
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

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

    DefaultResolvedDependencyResult newDependency(String group='a', String module='a', String version='1', String selectedVersion='1') {
        new DefaultResolvedDependencyResult(newSelector(group, module, version), false, newModule(), newModule(group, module, selectedVersion), Mock(ResolvedVariantResult))
    }

    DefaultUnresolvedDependencyResult newUnresolvedDependency(String group='x', String module='x', String version='1', String selectedVersion='1') {
        def requested = newSelector(group, module, version)
        org.gradle.internal.Factory<String> broken = { "broken" }
        new DefaultUnresolvedDependencyResult(requested, newModule(group, module, selectedVersion), false, new ModuleVersionResolveException(DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, module), version), broken), ComponentSelectionReasons.requested())
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
