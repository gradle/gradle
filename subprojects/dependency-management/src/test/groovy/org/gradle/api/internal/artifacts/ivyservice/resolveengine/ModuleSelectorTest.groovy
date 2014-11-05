/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine

import org.apache.ivy.core.module.descriptor.DefaultExcludeRule
import org.apache.ivy.core.module.descriptor.ExcludeRule
import org.apache.ivy.plugins.matcher.ExactPatternMatcher
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.ivyservice.IvyUtil.createArtifactId

class ModuleSelectorTest extends Specification {
    ModuleVersionSpec moduleVersionSpec = createModuleVersionSpec()
    ArtifactIdSpec artifactIdSpec = createArtifactIdSpec()
    ModuleSelector moduleSelector = new ModuleSelector(moduleVersionSpec, artifactIdSpec)

    def "specs are accessible"() {
        expect:
        moduleSelector.moduleVersionSpec == moduleVersionSpec
        moduleSelector.artifactIdSpec == artifactIdSpec
    }

    def "can create module selector for empty list of excludes"() {
        when:
        ModuleSelector moduleSelector = ModuleSelector.forExcludes()

        then:
        moduleSelector
        moduleSelector.moduleVersionSpec
        moduleSelector.artifactIdSpec
    }

    def "can create module selector for list of excludes"() {
        given:
        ExcludeRule rule1 = excludeArtifactsRule('org', 'module', 'test', 'jar', 'jar')
        ExcludeRule rule2 = excludeArtifactsRule('org', 'module', 'test', 'jar', 'jar')

        when:
        ModuleSelector moduleSelector = ModuleSelector.forExcludes(rule1, rule2)

        then:
        moduleSelector
        moduleSelector.moduleVersionSpec
        moduleSelector.artifactIdSpec
    }

    def "union of two module selectors"() {
        ModuleVersionSpec otherModuleVersionSpec = createModuleVersionSpec()
        ArtifactIdSpec otherArtifactIdSpec = createArtifactIdSpec()
        ModuleSelector otherModuleSelector = new ModuleSelector(otherModuleVersionSpec, otherArtifactIdSpec)

        when:
        ModuleSelector mergedModuleSelector = moduleSelector.union(otherModuleSelector)

        then:
        mergedModuleSelector
        1 * moduleVersionSpec.union(otherModuleSelector.moduleVersionSpec) >> ModuleVersionSpec.forExcludes()
        1 * artifactIdSpec.union(otherModuleSelector.artifactIdSpec) >> ArtifactIdSpec.forExcludes()
    }

    def "intersection of two module selectors"() {
        ModuleVersionSpec otherModuleVersionSpec = createModuleVersionSpec()
        ArtifactIdSpec otherArtifactIdSpec = createArtifactIdSpec()
        ModuleSelector otherModuleSelector = new ModuleSelector(otherModuleVersionSpec, otherArtifactIdSpec)

        when:
        ModuleSelector mergedModuleSelector = moduleSelector.intersect(otherModuleSelector)

        then:
        mergedModuleSelector
        1 * moduleVersionSpec.intersect(otherModuleSelector.moduleVersionSpec) >> ModuleVersionSpec.forExcludes()
        1 * artifactIdSpec.intersect(otherModuleSelector.artifactIdSpec) >> ArtifactIdSpec.forExcludes()
    }

    private ModuleVersionSpec createModuleVersionSpec() {
        Spy(ModuleVersionSpec) {
            isSatisfiedBy(_) >> false
            union(_) >> ModuleVersionSpec.forExcludes()
            intersect(_) >> ModuleVersionSpec.forExcludes()
        }
    }

    private ArtifactIdSpec createArtifactIdSpec() {
        Spy(ArtifactIdSpec) {
            isSatisfiedBy(_) >> false
            union(_) >> ArtifactIdSpec.forExcludes()
            intersect(_) >> ArtifactIdSpec.forExcludes()
        }
    }

    private ExcludeRule excludeArtifactsRule(String org, String module, String name, String type, String ext) {
        new DefaultExcludeRule(createArtifactId(org, module, name, type, ext), ExactPatternMatcher.INSTANCE, [:])
    }
}
