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

package org.gradle.internal.component.external.model

import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.LinkedHashMultimap
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.ConfigurationNotFoundException
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.Exclude

import static com.google.common.collect.ImmutableList.copyOf

class IvyDependencyDescriptorTest extends ExternalDependencyDescriptorTest {

    @Override
    ExternalDependencyDescriptor create(ModuleComponentSelector selector) {
        return new IvyDependencyDescriptor(selector, ImmutableListMultimap.of())
    }

    IvyDependencyDescriptor createWithExcludes(ModuleComponentSelector selector, List<Exclude> excludes) {
        return new IvyDependencyDescriptor(selector, "12", false, true, false, ImmutableListMultimap.of(), [], excludes)
    }

    IvyDependencyDescriptor createWithArtifacts(ModuleComponentSelector selector, List<Artifact> artifacts) {
        return new IvyDependencyDescriptor(selector, "12", false, true, false, ImmutableListMultimap.of(), artifacts, [])
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts"() {
        def metadata = createWithArtifacts(requested, [])
        def fromConfiguration = Stub(ConfigurationMetadata)

        expect:
        metadata.getConfigurationArtifacts(fromConfiguration).empty
    }

    def "uses artifacts defined by dependency descriptor"() {
        def artifact1 = new Artifact(new DefaultIvyArtifactName("art1", "type", "ext"), ["config"] as Set)
        def artifact2 = new Artifact(new DefaultIvyArtifactName("art2", "type", "ext"), ["other"] as Set)
        def artifact3 = new Artifact(new DefaultIvyArtifactName("art3", "type", "ext"), ["super"] as Set)

        given:
        def metadata = createWithArtifacts(requested, [artifact1, artifact2, artifact3])

        expect:
        metadata.dependencyArtifacts.size() == 3
        def artifacts = metadata.dependencyArtifacts
        artifacts[0] == artifact1
        artifacts[1] == artifact2
        artifacts[2] == artifact3
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts for source configuration"() {
        def artifact = new Artifact(new DefaultIvyArtifactName("art", "type", "ext"), ["other"] as Set)
        def metadata = createWithArtifacts(requested, [artifact])
        def fromConfiguration = Stub(ConfigurationMetadata)

        expect:
        metadata.getConfigurationArtifacts(fromConfiguration).empty
    }

    def "uses artifacts defined by dependency descriptor for specified source and target configurations "() {
        def artifact1 = new Artifact(new DefaultIvyArtifactName("art1", "type", "ext"), ["config"] as Set)
        def artifact2 = new Artifact(new DefaultIvyArtifactName("art2", "type", "ext"), ["other"] as Set)
        def artifact3 = new Artifact(new DefaultIvyArtifactName("art3", "type", "ext"), ["super"] as Set)

        def fromConfiguration = Stub(ConfigurationMetadata)

        given:
        fromConfiguration.hierarchy >> (['config', 'super'] as LinkedHashSet)
        def metadata = createWithArtifacts(requested, [artifact1, artifact2, artifact3])

        expect:
        metadata.getConfigurationArtifacts(fromConfiguration) == [artifact1.artifactName, artifact3.artifactName]
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = createWithExcludes(requested, [])
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration("from").hierarchy))) == ModuleExclusions.excludeNone()
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration("anything").hierarchy))) == ModuleExclusions.excludeNone()
    }

    def "excludes nothing when traversing a different configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration("anything").hierarchy))) == ModuleExclusions.excludeNone()
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("from")
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration.hierarchy))) == moduleExclusions.excludeAny(exclude)
    }

    def "applies rules when traversing a child of specified configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("child", "from")
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration.hierarchy))) == moduleExclusions.excludeAny(exclude)
    }

    def "applies matching exclude rules"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"), ["*"] as String[], PatternMatchers.EXACT)
        def exclude3 = new DefaultExclude(DefaultModuleIdentifier.newId("group3", "*"), ["other"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude1, exclude2, exclude3])
        def configuration = configuration("from")
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration.hierarchy))) == moduleExclusions.excludeAny(exclude1, exclude2)
    }

    def "selects no configurations when no configuration mappings provided"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        fromConfig.name >> "from"

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, ImmutableListMultimap.of(), [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent).empty
    }

    def "selects configurations from target component that match configuration mappings"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2] // verify order as well
    }

    def "selects matching configurations for super-configurations"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
    }

    def "configuration mapping can use wildcard on LHS"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can use wildcard on RHS to select all public configurations"() {
        def fromComponent = Stub(ComponentIdentifier)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        def toConfig1 = config('to-1', true)
        def toConfig2 = config('to-2', true)
        def toConfig3 = config('to-3', false)
        toComponent.getConfigurationNames() >> ["to-1", "to-2", "to-3"]
        toComponent.getConfiguration("to-1") >> toConfig1
        toComponent.getConfiguration("to-2") >> toConfig2
        toComponent.getConfiguration("to-3") >> toConfig3

        def configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "*")
        configMapping.put("from", "to-2")
        configMapping.put("other", "unknown")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
    }

    private ConfigurationMetadata config(name, visible) {
        def toConfig1 = Stub(ConfigurationMetadata)
        toConfig1.visible >> visible
        toConfig1.name >> name
        toConfig1.getHierarchy() >> [name]
        toConfig1
    }

    def "configuration mapping can use all-except-wildcard on LHS"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig3, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can include fallback on LHS"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig3]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1, toConfig3]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig3, toComponent) as List == [toConfig2, toConfig3]
    }

    def "configuration mapping can include fallback on RHS"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1, toConfig2]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig3, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can include self placeholder on RHS"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig1]
    }

    def "configuration mapping can include this placeholder on RHS"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig2]
    }

    def "configuration mapping can include wildcard on LHS and placeholder on RHS"() {
        def fromComponent = Stub(ComponentIdentifier)
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
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent) as List == [toConfig1]
        metadata.selectLegacyConfigurations(fromComponent, fromConfig2, toComponent) as List == [toConfig2]

        where:
        // these all map to the same thing
        lhs | rhs
        "*" | "@"
        "*" | "#"
        "%" | "@"
        "%" | "#"
    }

    def "fails when target component does not have matching configurations"() {
        def fromComponent = Stub(ComponentIdentifier) {
            getDisplayName() >> "thing a"
        }
        def toId = Stub(ComponentIdentifier) {
            getDisplayName() >> "thing b"
        }
        def toComponent = Stub(ComponentResolveMetadata)
        toComponent.id >> toId
        def fromConfig = Stub(ConfigurationMetadata)
        fromConfig.hierarchy >> ["from"]
        fromConfig.name >> "from"
        toComponent.getConfiguration(_) >> null

        def configMapping = LinkedHashMultimap.create()
        configMapping.put(lhs, rhs)

        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])

        when:
        metadata.selectLegacyConfigurations(fromComponent, fromConfig, toComponent)

        then:
        def e = thrown ConfigurationNotFoundException
        e.message == "Thing a declares a dependency from configuration 'from' to configuration 'to' which is not declared in the descriptor for thing b."

        where:
        lhs    | rhs
        "from" | "to"
        "*"    | "to"
        "%"    | "to"
        "%"    | "to(to)"
    }
}
