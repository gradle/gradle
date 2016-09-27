/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.component.model

import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import spock.lang.Specification

class LocalComponentDependencyMetadataTest extends Specification {
    def "returns this when same version requested"() {
        def dep = new LocalComponentDependencyMetadata(DefaultModuleComponentSelector.newSelector("a", "b", "12"), DefaultModuleVersionSelector.newSelector("a", "b", "12"), "from", null, "to", [] as Set, [], false, false, true)

        expect:
        dep.withRequestedVersion("12").is(dep)
        dep.withTarget(DefaultModuleComponentSelector.newSelector("a", "b", "12")).is(dep)
    }

    def "returns this when same target requested"() {
        def selector = Stub(ProjectComponentSelector)
        def dep = new LocalComponentDependencyMetadata(selector, Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)

        expect:
        dep.withTarget(selector).is(dep)
    }

    def "selects the target configuration from target component"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def toConfig = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]

        given:
        toComponent.getConfiguration("to") >> toConfig

        expect:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent) == [toConfig] as Set
    }

    def "fails to select target configuration when not present in the target component"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        fromComponent.componentId >> Stub(ComponentIdentifier) { getDisplayName() >> "thing a" }
        toComponent.componentId >> Stub(ComponentIdentifier) { getDisplayName() >> "thing b" }
        fromConfig.hierarchy >> ["from"]

        given:
        toComponent.getConfiguration("to") >> null

        when:
        dep.selectConfigurations(fromComponent, fromConfig, toComponent)

        then:
        def e = thrown(ConfigurationNotFoundException)
        e.message == "Thing a declares a dependency from configuration 'from' to configuration 'to' which is not declared in the descriptor for thing b."
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [], false, false, true)

        expect:
        def exclusions = dep.getExclusions(configuration("from"))
        exclusions == ModuleExclusions.excludeNone()
        exclusions.is(dep.getExclusions(configuration("other", "from")))
    }

    def "applies exclude rules when traversing the from configuration"() {
        def exclude1 = new DefaultExclude("group1", "*")
        def exclude2 = new DefaultExclude("group2", "*")
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", null, "to", [] as Set, [exclude1, exclude2], false, false, true)

        expect:
        def exclusions = dep.getExclusions(configuration("from"))
        exclusions == ModuleExclusions.excludeAny(exclude1, exclude2)
        exclusions.is(dep.getExclusions(configuration("other", "from")))
    }

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }
}
