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

import org.gradle.api.artifacts.result.ModuleVersionSelectionReason
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector
import static org.gradle.api.internal.artifacts.result.ResolutionResultDataBuilder.newModule

class CachingDependencyResultFactoryTest extends Specification {

    CachingDependencyResultFactory factory = new CachingDependencyResultFactory()

    def "creates and caches resolved dependencies"() {
        def fromModule = newModule('from')
        def selectedModule = newModule('selected')

        when:
        def dep = factory.createResolvedDependency(selector('requested'), fromModule, selectedModule)
        def same = factory.createResolvedDependency(selector('requested'), fromModule, selectedModule)

        def differentRequested = factory.createResolvedDependency(selector('xxx'), fromModule, selectedModule)
        def differentFrom = factory.createResolvedDependency(selector('requested'), newModule('xxx'), selectedModule)
        def differentSelected = factory.createResolvedDependency(selector('requested'), fromModule, newModule('xxx'))

        then:
        dep.is(same)
        !dep.is(differentFrom)
        !dep.is(differentRequested)
        !dep.is(differentSelected)
    }

    def "creates and caches unresolved dependencies"() {
        def fromModule = newModule('from')
        def selectedModule = Mock(ModuleVersionSelectionReason)

        when:
        def dep = factory.createUnresolvedDependency(selector('requested'), fromModule, selectedModule, new ModuleVersionResolveException(selector('requested'), "foo"))
        def same = factory.createUnresolvedDependency(selector('requested'), fromModule, selectedModule, new ModuleVersionResolveException(selector('requested'), "foo"))

        def differentRequested = factory.createUnresolvedDependency(selector('xxx'), fromModule, selectedModule, new ModuleVersionResolveException(selector('xxx'), "foo"))
        def differentFrom = factory.createUnresolvedDependency(selector('requested'), newModule('xxx'), selectedModule, new ModuleVersionResolveException(selector('requested'), "foo"))
        def differentFailure = factory.createUnresolvedDependency(selector('requested'), fromModule, selectedModule, new ModuleVersionResolveException(selector('requested'), "foo"))

        then:
        dep.is(same)
        !dep.is(differentFrom)
        !dep.is(differentRequested)
        dep.is(differentFailure) //the same dependency edge cannot have different failures
    }

    def selector(String group='a', String module='a', String version='1') {
        newSelector(group, module, version)
    }
}