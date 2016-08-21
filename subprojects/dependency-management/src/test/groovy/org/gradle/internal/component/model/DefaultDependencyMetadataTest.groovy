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

package org.gradle.internal.component.model

import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

abstract class DefaultDependencyMetadataTest extends Specification {
    def requested = newSelector("org", "module", "1.2+")
    def id = DefaultModuleVersionIdentifier.newId("org", "module", "1.2+")

    abstract DefaultDependencyMetadata create(ModuleVersionSelector selector)

    abstract DefaultDependencyMetadata createWithExcludes(ModuleVersionSelector selector, List<Exclude> excludes)

    abstract DefaultDependencyMetadata createWithArtifacts(ModuleVersionSelector selector, List<Artifact> artifacts)

    def "creates a copy with new requested version"() {
        def metadata = create(requested)

        given:

        when:
        def copy = metadata.withRequestedVersion("1.3+")

        then:
        copy != metadata
        copy.requested == newSelector("org", "module", "1.3+")
        copy.selector == DefaultModuleComponentSelector.newSelector("org", "module", "1.3+")
    }

    def "returns this if new requested version is the same as current requested version"() {
        def metadata = create(requested)

        expect:
        metadata.withRequestedVersion("1.2+").is(metadata)
        metadata.withTarget(DefaultModuleComponentSelector.newSelector("org", "module", "1.2+")).is(metadata)
    }

    def "creates a copy with new requested project selector"() {
        def metadata = create(requested)
        def selector = DefaultProjectComponentSelector.newSelector(":project")

        when:
        def copy = metadata.withTarget(selector)

        then:
        copy != metadata
        copy.requested == requested
        copy.selector == selector
        copy.moduleConfigurations == metadata.moduleConfigurations
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts"() {
        def metadata = createWithArtifacts(requested, [])
        def fromConfiguration = Stub(ConfigurationMetadata)
        def toConfiguration = Stub(ConfigurationMetadata)

        expect:
        metadata.getArtifacts(fromConfiguration, toConfiguration).empty
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts for source configuration"() {
        def artifact = new Artifact(new DefaultIvyArtifactName("art", "type", "ext"), ["other"] as Set)
        def metadata = createWithArtifacts(requested, [artifact])
        def fromConfiguration = Stub(ConfigurationMetadata)
        def toConfiguration = Stub(ConfigurationMetadata)

        expect:
        metadata.getArtifacts(fromConfiguration, toConfiguration).empty
    }

    def "uses artifacts defined by dependency descriptor for specified source and target configurations "() {
        def artifact1 = new Artifact(new DefaultIvyArtifactName("art1", "type", "ext"), ["config"] as Set)
        def artifact2 = new Artifact(new DefaultIvyArtifactName("art2", "type", "ext"), ["other"] as Set)
        def artifact3 = new Artifact(new DefaultIvyArtifactName("art3", "type", "ext"), ["super"] as Set)

        def fromConfiguration = Stub(ConfigurationMetadata)
        def toConfiguration = Stub(ConfigurationMetadata)
        def compArtifact1 = Stub(ComponentArtifactMetadata)
        def compArtifact3 = Stub(ComponentArtifactMetadata)

        given:
        fromConfiguration.hierarchy >> (['config', 'super'] as LinkedHashSet)

        def metadata = createWithArtifacts(requested, [artifact1, artifact2, artifact3])
        toConfiguration.artifact(artifact1.artifactName) >> compArtifact1
        toConfiguration.artifact(artifact3.artifactName) >> compArtifact3

        expect:
        metadata.getArtifacts(fromConfiguration, toConfiguration) == [compArtifact1, compArtifact3] as Set
    }

    def "uses artifacts defined by dependency descriptor"() {
        def artifact1 = new Artifact(new DefaultIvyArtifactName("art1", "type", "ext"), ["config"] as Set)
        def artifact2 = new Artifact(new DefaultIvyArtifactName("art2", "type", "ext"), ["other"] as Set)
        def artifact3 = new Artifact(new DefaultIvyArtifactName("art3", "type", "ext"), ["super"] as Set)

        given:
        def metadata = createWithArtifacts(requested, [artifact1, artifact2, artifact3])

        expect:
        metadata.artifacts.size() == 3
        def artifacts = metadata.artifacts
        artifacts[0] == artifact1.artifactName
        artifacts[1] == artifact2.artifactName
        artifacts[2] == artifact3.artifactName
    }

    def "returns a module component selector if descriptor indicates a default dependency"() {
        given:
        def metadata = create(requested)

        when:
        ComponentSelector componentSelector = metadata.getSelector()

        then:
        componentSelector instanceof ModuleComponentSelector
        componentSelector.group == 'org'
        componentSelector.module == 'module'
        componentSelector.version == '1.2+'
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = createWithExcludes(requested, [])

        expect:
        dep.getExclusions(configuration("from")) == ModuleExclusions.excludeNone()
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "excludes nothing when traversing a different configuration"() {
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])

        expect:
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
    }

    def "applies rules when traversing a child of specified configuration"() {
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("child", "from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
    }

    def "applies matching exclude rules"() {
        def exclude1 = new DefaultExclude("group1", "*", ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude("group2", "*", ["*"] as String[], PatternMatchers.EXACT)
        def exclude3 = new DefaultExclude("group3", "*", ["other"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude1, exclude2, exclude3])
        def configuration = configuration("from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude1, exclude2)
    }

    def configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ([name] as Set) + (parents as Set)
        return config
    }
}
