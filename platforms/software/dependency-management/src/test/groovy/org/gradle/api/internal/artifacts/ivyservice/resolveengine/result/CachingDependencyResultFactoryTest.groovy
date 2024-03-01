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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.result

import org.gradle.api.artifacts.result.ComponentSelectionReason
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.resolve.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newModule
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newVariant

class CachingDependencyResultFactoryTest extends Specification {

    CachingDependencyResultFactory factory = new CachingDependencyResultFactory()

    def "creates and caches resolved dependencies"() {
        def fromModule = newModule('from')
        def selectedModule = newModule('selected')
        def variant = newVariant("foo")

        when:
        def dep = factory.createResolvedDependency(selector('requested'), fromModule, selectedModule, variant, false)
        def same = factory.createResolvedDependency(selector('requested'), fromModule, selectedModule, variant, false)

        def differentRequested = factory.createResolvedDependency(selector('xxx'), fromModule, selectedModule, variant, false)
        def differentFrom = factory.createResolvedDependency(selector('requested'), newModule('xxx'), selectedModule, variant, false)
        def differentSelected = factory.createResolvedDependency(selector('requested'), fromModule, newModule('xxx'), variant, false)
        def differentConstraint = factory.createResolvedDependency(selector('requested'), fromModule, selectedModule, variant, true)

        then:
        dep.is(same)
        !dep.is(differentFrom)
        !dep.is(differentRequested)
        !dep.is(differentSelected)
        !dep.is(differentConstraint)
    }

    def "creates and caches resolved dependencies with attributes"() {
        def fromModule = newModule('from')
        def selectedModule = newModule('selected', 'a', '1', selectedByRule(), newVariant('custom', [attr1: 'foo', attr2: 'bar']))
        def variant = newVariant("foo")

        when:
        def dep = factory.createResolvedDependency(selector('requested'), fromModule, selectedModule, variant, false)
        def same = factory.createResolvedDependency(selector('requested'), fromModule, selectedModule, variant, false)

        def differentRequested = factory.createResolvedDependency(selector('xxx'), fromModule, selectedModule, variant, false)
        def differentFrom = factory.createResolvedDependency(selector('requested'), newModule('xxx'), selectedModule, variant, false)
        def differentSelected = factory.createResolvedDependency(selector('requested'), fromModule, newModule('xxx'), variant, false)

        then:
        dep.is(same)
        !dep.is(differentFrom)
        !dep.is(differentRequested)
        !dep.is(differentSelected)
    }

    def "creates and caches unresolved dependencies"() {
        def fromModule = newModule('from')
        def selectedModule = Mock(ComponentSelectionReason)
        org.gradle.internal.Factory<String> broken = { " foo" }

        when:
        def dep = factory.createUnresolvedDependency(selector('requested'), fromModule, false, selectedModule, new ModuleVersionResolveException(moduleVersionSelector('requested'), broken))
        def same = factory.createUnresolvedDependency(selector('requested'), fromModule, false, selectedModule, new ModuleVersionResolveException(moduleVersionSelector('requested'), broken))

        def differentRequested = factory.createUnresolvedDependency(selector('xxx'), fromModule, false, selectedModule, new ModuleVersionResolveException(moduleVersionSelector('xxx'), broken))
        def differentFrom = factory.createUnresolvedDependency(selector('requested'), newModule('xxx'), false, selectedModule, new ModuleVersionResolveException(moduleVersionSelector('requested'), broken))
        def differentConstraint = factory.createUnresolvedDependency(selector('requested'), fromModule, true, selectedModule, new ModuleVersionResolveException(moduleVersionSelector('requested'), broken))
        def differentFailure = factory.createUnresolvedDependency(selector('requested'), fromModule, false, selectedModule, new ModuleVersionResolveException(moduleVersionSelector('requested'), broken))

        then:
        dep.is(same)
        !dep.is(differentFrom)
        !dep.is(differentRequested)
        !dep.is(differentConstraint)
        dep.is(differentFailure) //the same dependency edge cannot have different failures
    }

    def selector(String group = 'a', String module = 'a', String version = '1') {
        DefaultModuleComponentSelector.newSelector(DefaultModuleIdentifier.newId(group, module), new DefaultMutableVersionConstraint(version))
    }

    def moduleVersionSelector(String group = 'a', String module = 'a', String version = '1') {
        newSelector(DefaultModuleIdentifier.newId(group, module), version)
    }

    private static ComponentSelectionReason selectedByRule() {
        ComponentSelectionReasons.of(ComponentSelectionReasons.SELECTED_BY_RULE)
    }
}
