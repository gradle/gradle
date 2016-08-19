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

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.LinkedHashMultimap
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class DefaultDependencyMetadataTest extends Specification {
    def requested = newSelector("org", "module", "1.2+")
    def id = DefaultModuleVersionIdentifier.newId("org", "module", "1.2+")

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
        def metadata = new DefaultDependencyMetadata(id)

        given:

        when:
        def copy = metadata.withRequestedVersion("1.3+")

        then:
        copy.requested == newSelector("org", "module", "1.3+")
    }

    def "returns this if new requested version is the same as current requested version"() {
        def metadata = new DefaultDependencyMetadata(id)

        expect:
        metadata.withRequestedVersion("1.2+").is(metadata)
        metadata.withTarget(DefaultModuleComponentSelector.newSelector("org", "module", "1.2+")).is(metadata)
    }

    def "can set changing flag"() {
        def metadata = new DefaultDependencyMetadata(id)

        expect:
        !metadata.changing

        when:
        def copy = metadata.withChanging()

        then:
        copy.requested == requested
        copy.changing
    }

    def "returns this when changing is already true"() {
        def metadata = new DefaultDependencyMetadata(id).withChanging()

        expect:
        metadata.withChanging().is(metadata)
    }

    def "selects no configurations when no configuration mappings provided"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        fromConfig.name >> "from"

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent).empty
    }

    def "selects configurations from target component that match configuration mappings"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("from", "to-2")
        configMapping.put("other", "unknown")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2] // verify order as well
    }

    def "selects matching configurations for super-configurations"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from", "super"]
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("super", "to-2")
        configMapping.put("other", "unknown")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
    }

    def "configuration mapping can use wildcard on LHS"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def fromConfig2 = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        fromConfig2.hierarchy >> ["other"]
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("*", "to-2")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can use wildcard on RHS to select all public configurations"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        def toConfig3 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        toConfig1.visible >> true
        toConfig2.visible >> true
        toConfig3.visible >> false
        toComponent.getConfigurationNames() >> ["to-1", "to-2", "to-3"]
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2
        toComponent.getConfiguration("to-3") >> toConfig3

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "*")
        configMapping.put("from", "to-2")
        configMapping.put("other", "unknown")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
    }

    def "configuration mapping can use all-except-wildcard on LHS"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def fromConfig2 = Stub(ConfigurationMetadata)
        def fromConfig3 = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        fromConfig2.hierarchy >> ["child", "from"]
        fromConfig3.hierarchy >> ["other"]
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("*", "to-2")
        configMapping.put("!from", "to-2")
        configMapping.put("from", "to-1")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig3, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can include fallback on LHS"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def fromConfig2 = Stub(ConfigurationMetadata)
        def fromConfig3 = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        def toConfig3 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        fromConfig2.hierarchy >> ["child", "from"]
        fromConfig3.hierarchy >> ["other"]
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2
        toComponent.getConfiguration("to-3") >> toConfig3

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("%", "to-2")
        configMapping.put("*", "to-3")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig3]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1, toConfig3]
        metadata.selectConfigurations(fromComponent, fromConfig3, toComponent) as List == [toConfig2, toConfig3]
    }

    def "configuration mapping can include fallback on RHS"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def fromConfig2 = Stub(ConfigurationMetadata)
        def fromConfig3 = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        fromConfig2.hierarchy >> ["other"]
        fromConfig3.hierarchy >> ["other2"]
        toConfig1.visible >> true
        toConfig2.visible >> true
        toComponent.getConfigurationNames() >> ["to-1", "to-2"]
        toComponent.getConfiguration("unknown") >> null
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "unknown(*)")
        configMapping.put("other", "unknown(to-1)")
        configMapping.put("other2", "to-2(unknown)")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig3, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can include self placeholder on RHS"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def fromConfig2 = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["a"]
        fromConfig2.hierarchy >> ["other", "a"]
        toComponent.getConfiguration("a") >> toConfig1

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("a", "@")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1]
    }

    def "configuration mapping can include this placeholder on RHS"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def fromConfig2 = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        fromConfig.name >> "a"
        fromConfig2.name >> "b"
        fromConfig.hierarchy >> ["a"]
        fromConfig2.hierarchy >> ["b", "a"]
        toComponent.getConfiguration("a") >> toConfig1
        toComponent.getConfiguration("b") >> toConfig2

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("a", "#")

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can include wildcard on LHS and placeholder on RHS"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        def fromConfig2 = Stub(ConfigurationMetadata)
        def toConfig1 = Stub(ConfigurationMetadata)
        def toConfig2 = Stub(ConfigurationMetadata)
        fromConfig.name >> "a"
        fromConfig2.name >> "b"
        fromConfig.hierarchy >> ["a"]
        fromConfig2.hierarchy >> ["b", "a"]
        toComponent.getConfiguration("a") >> toConfig1
        toComponent.getConfiguration("b") >> toConfig2

        def configMapping = LinkedHashMultimap.create()
        configMapping.put(lhs, rhs)

        expect:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig2]

        where:
        // these all map to the same thing
        lhs | rhs
        "*" | "@"
        "*" | "#"
        "%" | "@"
        "%" | "#"
    }

    def "fails when target component does not have matching configurations"() {
        def fromId = Stub(ComponentIdentifier) {
            getDisplayName() >> "thing a"
        }
        def toId = Stub(ComponentIdentifier) {
            getDisplayName() >> "thing b"
        }
        def fromComponent = Stub(ComponentResolveMetadata)
        fromComponent.componentId >> fromId
        def toComponent = Stub(ComponentResolveMetadata)
        toComponent.componentId >> toId
        def fromConfig = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        fromConfig.name >> "from"
        toComponent.getConfiguration(_) >> null

        def configMapping = LinkedHashMultimap.create()
        configMapping.put(lhs, rhs)

        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])

        when:
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent)

        then:
        ConfigurationNotFoundException e = thrown()
        e.message == "Thing a declares a dependency from configuration 'from' to configuration 'to' which is not declared in the descriptor for thing b."

        where:
        lhs    | rhs
        "from" | "to"
        "*"    | "to"
        "%"    | "to"
        "%"    | "to(to)"
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts"() {
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [])
        def fromConfiguration = Stub(ConfigurationMetadata)
        def toConfiguration = Stub(ConfigurationMetadata)

        expect:
        metadata.getArtifacts(fromConfiguration, toConfiguration).empty
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts for source configuration"() {
        def artifact = new Artifact(new DefaultIvyArtifactName("art", "type", "ext"), ["other"] as Set)
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [artifact], [])
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

        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [artifact1, artifact2, artifact3], [])
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
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [artifact1, artifact2, artifact3], [])

        expect:
        metadata.artifacts.size() == 3
        def artifacts = metadata.artifacts
        artifacts[0] == artifact1.artifactName
        artifacts[1] == artifact2.artifactName
        artifacts[2] == artifact3.artifactName
    }

    def "returns a module component selector if descriptor indicates a default dependency"() {
        given:
        def metadata = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [])

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
        def metadata = new DefaultDependencyMetadata(requested, "12", true, changing, transitive, ImmutableListMultimap.of(), [], [])

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
        def dep = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [])

        expect:
        dep.getExclusions(configuration("from")) == ModuleExclusions.excludeNone()
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "excludes nothing when traversing a different configuration"() {
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        def dep = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [exclude])

        expect:
        dep.getExclusions(configuration("anything")) == ModuleExclusions.excludeNone()
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        def dep = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [exclude])
        def configuration = configuration("from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
    }

    def "applies rules when traversing a child of specified configuration"() {
        def exclude = new DefaultExclude("group", "*", ["from"] as String[], PatternMatchers.EXACT)
        def dep = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [exclude])
        def configuration = configuration("child", "from")

        expect:
        dep.getExclusions(configuration) == ModuleExclusions.excludeAny(exclude)
    }

    def "applies matching exclude rules"() {
        def exclude1 = new DefaultExclude("group1", "*", ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude("group2", "*", ["*"] as String[], PatternMatchers.EXACT)
        def exclude3 = new DefaultExclude("group3", "*", ["other"] as String[], PatternMatchers.EXACT)
        def dep = new DefaultDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [exclude1, exclude2, exclude3])
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
