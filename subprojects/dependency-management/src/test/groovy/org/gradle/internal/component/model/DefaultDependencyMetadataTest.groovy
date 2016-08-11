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

import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.descriptor.Dependency
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultDependencyMetadataTest extends Specification {
    def requested = newSelector("org", "module", "1.2+")
    def descriptor = new Dependency(requested, "foo", false, false, false)

    def "constructs meta-data from descriptor"() {
        def metadata = new DefaultDependencyMetadata(descriptor)

        expect:
        metadata.requested == requested
    }

    def "constructs meta-data from component id"() {
        def id = new DefaultModuleComponentIdentifier("org", "module", "1.1")
        def metadata = new DefaultDependencyMetadata(id)

        expect:
        metadata.requested == newSelector("org", "module", "1.1")
    }

    def "constructs meta-data from module version id"() {
        def id = new DefaultModuleVersionIdentifier("org", "module", "1.1")
        def metadata = new DefaultDependencyMetadata(id)

        expect:
        metadata.requested == newSelector("org", "module", "1.1")
    }

    def "creates a copy with new requested version"() {
        def metadata = new DefaultDependencyMetadata(descriptor)

        given:

        when:
        def copy = metadata.withRequestedVersion("1.3+")

        then:
        copy.requested == newSelector("org", "module", "1.3+")
    }

    def "returns this if new requested version is the same as current requested version"() {
        def metadata = new DefaultDependencyMetadata(descriptor)

        expect:
        metadata.withRequestedVersion("1.2+").is(metadata)
        metadata.withTarget(DefaultModuleComponentSelector.newSelector("org", "module", "1.2+")).is(metadata)
    }

    def "can set changing flag"() {
        def metadata = new DefaultDependencyMetadata(descriptor)

        expect:
        !metadata.changing

        when:
        def copy = metadata.withChanging()

        then:
        copy.requested == requested
        copy.changing
    }

    def "returns this when changing is already true"() {
        def metadata = new DefaultDependencyMetadata(descriptor).withChanging()

        expect:
        metadata.withChanging().is(metadata)
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts"() {
        def metadata = new DefaultDependencyMetadata(descriptor)
        def fromConfiguration = Stub(ConfigurationMetadata)
        def toConfiguration = Stub(ConfigurationMetadata)

        expect:
        metadata.getArtifacts(fromConfiguration, toConfiguration).empty
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts for source configuration"() {
        def metadata = new DefaultDependencyMetadata(descriptor)
        def fromConfiguration = Stub(ConfigurationMetadata)
        def toConfiguration = Stub(ConfigurationMetadata)

        given:
        descriptor.dependencyArtifacts.add(new Artifact(new DefaultIvyArtifactName("art", "type", "ext"), ["other"] as Set))

        expect:
        metadata.getArtifacts(fromConfiguration, toConfiguration).empty
    }

    def "uses artifacts defined by dependency descriptor for specified source and target configurations "() {
        def fromConfiguration = Stub(ConfigurationMetadata)
        def targetComponent = Stub(ComponentResolveMetadata)
        def toConfiguration = Stub(ConfigurationMetadata)
        def artifact1 = Stub(ComponentArtifactMetadata)
        def artifact3 = Stub(ComponentArtifactMetadata)

        given:
        fromConfiguration.hierarchy >> (['config', 'super'] as LinkedHashSet)
        toConfiguration.component >> targetComponent
        addArtifact(descriptor, "config", "art1")
        addArtifact(descriptor, "other", "art2")
        addArtifact(descriptor, "super", "art3")

        def metadata = new DefaultDependencyMetadata(descriptor)
        toConfiguration.artifact({it.name == 'art1'}) >> artifact1
        toConfiguration.artifact({it.name == 'art3'}) >> artifact3

        expect:
        metadata.getArtifacts(fromConfiguration, toConfiguration) == [artifact1, artifact3] as Set
    }

    def "uses artifacts defined by dependency descriptor"() {
        given:
        addArtifact(descriptor, "config", "art1")
        addArtifact(descriptor, "other", "art2")
        addArtifact(descriptor, "super", "art3")
        def metadata = new DefaultDependencyMetadata(descriptor)

        expect:
        metadata.artifacts.size() == 3
        def artifacts = metadata.artifacts.sort { it.name }
        artifacts[0].name == 'art1'
        artifacts[1].name == 'art2'
        artifacts[2].name == 'art3'
    }

    private static addArtifact(Dependency descriptor, String config, String name) {
        IvyArtifactName artifactName = new DefaultIvyArtifactName(name, "type", "ext")
        descriptor.dependencyArtifacts.add(new Artifact(artifactName, [config] as Set))
    }

    def "returns a module component selector if descriptor indicates a default dependency"() {
        given:
        def metadata = new DefaultDependencyMetadata(descriptor)

        when:
        ComponentSelector componentSelector = metadata.getSelector()

        then:
        componentSelector instanceof ModuleComponentSelector
        componentSelector.group == 'org'
        componentSelector.module == 'module'
        componentSelector.version == '1.2+'
    }

    def "retains transitive and changing flags in substituted dependency"() {
        given:
        def descriptor = new Dependency(requested, "foo", false, changing, transitive)
        def metadata = new DefaultDependencyMetadata(descriptor)

        when:
        DependencyMetadata replacedMetadata = metadata.withTarget(DefaultProjectComponentSelector.newSelector("test"))

        then:
        replacedMetadata.getSelector() instanceof ProjectComponentSelector
        replacedMetadata.isTransitive() == metadata.isTransitive()
        replacedMetadata.isChanging() == metadata.isChanging()

        where:
        transitive | changing
        true       | true
        false      | true
        true       | false
        false      | false
    }

    def "excludes nothing when no exclude rules provided"() {
        def descriptor = new Dependency(requested, "foo", false, false, true)
        def dep = new DefaultDependencyMetadata(descriptor)

        expect:
        dep.getExclusions(configuration("from")) == ModuleExclusions.excludeNone()
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "excludes nothing when traversing a different configuration"() {
        def descriptor = new Dependency(requested, "foo", false, false, true)
        descriptor.addExcludeRule(new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT))
        def dep = new DefaultDependencyMetadata(descriptor)

        expect:
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "applies and caches exclude rules"() {
        def descriptor = new Dependency(requested, "foo", false, false, true)
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        descriptor.addExcludeRule(exclude)
        def dep = new DefaultDependencyMetadata(descriptor)
        def configuration = configuration("from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
    }

    def "applies and caches exclude rules when traversing a child of specified configuration"() {
        def descriptor = new Dependency(requested, "foo", false, false, true)
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        descriptor.addExcludeRule(exclude)
        def dep = new DefaultDependencyMetadata(descriptor)
        def configuration = configuration("child", "from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
    }

    def "applies and caches matching exclude rules"() {
        def descriptor = new Dependency(requested, "foo", false, false, true)
        def exclude1 = new DefaultExclude("group1", "*", ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude("group2", "*", ["*"] as String[], PatternMatchers.EXACT)
        def exclude3 = new DefaultExclude("group3", "*", ["other"] as String[], PatternMatchers.EXACT)
        descriptor.addExcludeRule(exclude1)
        descriptor.addExcludeRule(exclude2)
        descriptor.addExcludeRule(exclude3)
        def dep = new DefaultDependencyMetadata(descriptor)
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
