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
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.internal.component.external.descriptor.DefaultExclude
import spock.lang.Specification

class LocalComponentDependencyMetadataTest extends Specification {
    def "excludes nothing when no exclude rules provided"() {
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", "to", [] as Set, [], false, false, true)

        expect:
        dep.getExclusions(configuration("from")) == ModuleExclusions.excludeNone()
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "excludes nothing when traversing a different configuration"() {
        def exclude = new DefaultExclude("group", "*")
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", "to", [] as Set, [exclude], false, false, true)

        expect:
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "applies and caches exclude rules when traversing the from configuration"() {
        def exclude = new DefaultExclude("group", "*")
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", "to", [] as Set, [exclude], false, false, true)
        def configuration = configuration("from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
        dep.getExclusions(configuration).is(dep.getExclusions(configuration))
    }

    def "applies and caches exclude rules when traversing a child of from configuration"() {
        def exclude = new DefaultExclude("group", "*")
        def dep = new LocalComponentDependencyMetadata(Stub(ComponentSelector), Stub(ModuleVersionSelector), "from", "to", [] as Set, [exclude], false, false, true)
        def configuration = configuration("child", "from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
        dep.getExclusions(configuration).is(dep.getExclusions(configuration))
    }

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }
}
