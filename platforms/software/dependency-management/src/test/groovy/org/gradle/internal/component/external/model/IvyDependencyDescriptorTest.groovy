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

import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableListMultimap
import com.google.common.collect.ImmutableSet
import com.google.common.collect.LinkedHashMultimap
import com.google.common.collect.Multimap
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.specs.ExcludeSpec
import org.gradle.internal.component.ResolutionFailureHandler
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.external.model.ivy.IvyComponentGraphResolveState
import org.gradle.internal.component.external.model.ivy.IvyDependencyDescriptor
import org.gradle.internal.component.external.model.ivy.IvyModuleResolveMetadata
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata
import org.gradle.internal.component.model.ConfigurationGraphResolveState
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.model.Exclude
import org.gradle.internal.component.model.ModuleConfigurationMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import org.gradle.internal.component.resolution.failure.exception.AbstractResolutionFailureException

import static com.google.common.collect.ImmutableList.copyOf

class IvyDependencyDescriptorTest extends ExternalDependencyDescriptorTest {

    private final static ExcludeSpec NOTHING = new ModuleExclusions().nothing()

    def resolutionFailureHandler = Mock(ResolutionFailureHandler)

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
        fromConfiguration.hierarchy >> ImmutableSet.of('config', 'super')
        def metadata = createWithArtifacts(requested, [artifact1, artifact2, artifact3])

        expect:
        metadata.getConfigurationArtifacts(fromConfiguration) == [artifact1.artifactName, artifact3.artifactName]
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = createWithExcludes(requested, [])
        def moduleExclusions = new ModuleExclusions()

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration("from").hierarchy))) == NOTHING
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration("anything").hierarchy))) == NOTHING
    }

    def "excludes nothing when traversing a different configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def moduleExclusions = new ModuleExclusions()

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration("anything").hierarchy))) == NOTHING
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("from")
        def moduleExclusions = new ModuleExclusions()

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration.hierarchy))) == moduleExclusions.excludeAny(ImmutableList.of(exclude))
    }

    def "applies rules when traversing a child of specified configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("child", "from")
        def moduleExclusions = new ModuleExclusions()

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration.hierarchy))) == moduleExclusions.excludeAny(ImmutableList.of(exclude))
    }

    def "applies matching exclude rules"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"), ["*"] as String[], PatternMatchers.EXACT)
        def exclude3 = new DefaultExclude(DefaultModuleIdentifier.newId("group3", "*"), ["other"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude1, exclude2, exclude3])
        def configuration = configuration("from")
        def moduleExclusions = new ModuleExclusions()

        expect:
        moduleExclusions.excludeAny(copyOf(dep.getConfigurationExcludes(configuration.hierarchy))) == moduleExclusions.excludeAny(ImmutableList.of(exclude1, exclude2))
    }

    def "selects no configurations when no configuration mappings provided"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        fromConfig.name >> "from"

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, ImmutableListMultimap.of(), [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants.empty
    }

    def "selects configurations from target component that match configuration mappings"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "to-1")
        def toConfig2 = configuration(toComponent, "to-2")
        fromConfig.hierarchy >> ImmutableSet.of("from")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("from", "to-2")
        configMapping.put("other", "unknown")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1, toConfig2] // verify order as well
    }

    def "selects matching configurations for super-configurations"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "to-1")
        def toConfig2 = configuration(toComponent, "to-2")
        fromConfig.hierarchy >> ImmutableSet.of("from", "super")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("super", "to-2")
        configMapping.put("other", "unknown")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1, toConfig2]
    }

    def "configuration mapping can use wildcard on LHS"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def fromConfig2 = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "to-1")
        def toConfig2 = configuration(toComponent, "to-2")
        fromConfig.hierarchy >> ImmutableSet.of("from")
        fromConfig2.hierarchy >> ImmutableSet.of("other")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("*", "to-2")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1, toConfig2]
        metadata.selectLegacyConfigurations(fromConfig2, toComponent, resolutionFailureHandler).variants == [toConfig2]
    }

    def "configuration mapping can use wildcard on RHS to select all public configurations"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        fromConfig.hierarchy >> ImmutableSet.of("from")
        def toConfig1 = configuration(toComponent, "to-1")
        def toConfig2 = configuration(toComponent, "to-2")
        def toConfig3 = config('to-3', false)
        toComponent.getConfigurationNames() >> ["to-1", "to-2", "to-3"]
        toComponent.getConfiguration("to-3") >> toConfig3

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "*")
        configMapping.put("from", "to-2")
        configMapping.put("other", "unknown")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1, toConfig2]
    }

    private ConfigurationGraphResolveState config(name, visible) {
        def toConfig = Stub(ConfigurationGraphResolveMetadata)
        toConfig.isVisible() >> visible
        toConfig.name >> name
        toConfig.getHierarchy() >> ImmutableSet.of(name)
        def toState = Stub(ConfigurationGraphResolveState)
        toState.metadata >> toConfig
        toState.asVariant() >> { throw new RuntimeException() }
        return toState
    }

    def "configuration mapping can use all-except-wildcard on LHS"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def fromConfig2 = Stub(ModuleConfigurationMetadata)
        def fromConfig3 = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "to-1")
        def toConfig2 = configuration(toComponent, "to-2")
        fromConfig.hierarchy >> ImmutableSet.of("from")
        fromConfig2.hierarchy >> ImmutableSet.of("child", "from")
        fromConfig3.hierarchy >> ImmutableSet.of("other")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("*", "to-2")
        configMapping.put("!from", "to-2")
        configMapping.put("from", "to-1")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1]
        metadata.selectLegacyConfigurations(fromConfig2, toComponent, resolutionFailureHandler).variants == [toConfig1]
        metadata.selectLegacyConfigurations(fromConfig3, toComponent, resolutionFailureHandler).variants == [toConfig2]
    }

    def "configuration mapping can include fallback on LHS"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def fromConfig2 = Stub(ModuleConfigurationMetadata)
        def fromConfig3 = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "to-1")
        def toConfig2 = configuration(toComponent, "to-2")
        def toConfig3 = configuration(toComponent, "to-3")
        fromConfig.hierarchy >> ImmutableSet.of("from")
        fromConfig2.hierarchy >> ImmutableSet.of("child", "from")
        fromConfig3.hierarchy >> ImmutableSet.of("other")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "to-1")
        configMapping.put("%", "to-2")
        configMapping.put("*", "to-3")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1, toConfig3]
        metadata.selectLegacyConfigurations(fromConfig2, toComponent, resolutionFailureHandler).variants == [toConfig1, toConfig3]
        metadata.selectLegacyConfigurations(fromConfig3, toComponent, resolutionFailureHandler).variants == [toConfig2, toConfig3]
    }

    def "configuration mapping can include fallback on RHS"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def toComponentMetadata = Stub(IvyModuleResolveMetadata)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def fromConfig2 = Stub(ModuleConfigurationMetadata)
        def fromConfig3 = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "to-1")
        def toConfig2 = configuration(toComponent, "to-2")
        fromConfig.hierarchy >> ImmutableSet.of("from")
        fromConfig2.hierarchy >> ImmutableSet.of("other")
        fromConfig3.hierarchy >> ImmutableSet.of("other2")
        toConfig1.visible >> true
        toConfig2.visible >> true
        toComponent.metadata >> toComponentMetadata
        toComponent.getConfigurationNames() >> ["to-1", "to-2"]
        toComponent.getConfiguration(_) >> null

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("from", "unknown(*)")
        configMapping.put("other", "unknown(to-1)")
        configMapping.put("other2", "to-2(unknown)")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1, toConfig2]
        metadata.selectLegacyConfigurations(fromConfig2, toComponent, resolutionFailureHandler).variants == [toConfig1]
        metadata.selectLegacyConfigurations(fromConfig3, toComponent, resolutionFailureHandler).variants == [toConfig2]
    }

    def "configuration mapping can include self placeholder on RHS"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def fromConfig2 = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "a")
        fromConfig.hierarchy >> ImmutableSet.of("a")
        fromConfig2.hierarchy >> ImmutableSet.of("other", "a")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("a", "@")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1]
        metadata.selectLegacyConfigurations(fromConfig2, toComponent, resolutionFailureHandler).variants == [toConfig1]
    }

    def "configuration mapping can include this placeholder on RHS"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def fromConfig2 = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "a")
        def toConfig2 = configuration(toComponent, "b")
        fromConfig.name >> "a"
        fromConfig2.name >> "b"
        fromConfig.hierarchy >> ImmutableSet.of("a")
        fromConfig2.hierarchy >> ImmutableSet.of("b", "a")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put("a", "#")

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1]
        metadata.selectLegacyConfigurations(fromConfig2, toComponent, resolutionFailureHandler).variants == [toConfig2]
    }

    def "configuration mapping can include wildcard on LHS and placeholder on RHS"() {
        def toComponent = Stub(IvyComponentGraphResolveState)
        def fromConfig = Stub(ModuleConfigurationMetadata)
        def fromConfig2 = Stub(ModuleConfigurationMetadata)
        def toConfig1 = configuration(toComponent, "a")
        def toConfig2 = configuration(toComponent, "b")
        fromConfig.name >> "a"
        fromConfig2.name >> "b"
        fromConfig.hierarchy >> ImmutableSet.of("a")
        fromConfig2.hierarchy >> ImmutableSet.of("b", "a")

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put(lhs, rhs)

        expect:
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler).variants == [toConfig1]
        metadata.selectLegacyConfigurations(fromConfig2, toComponent, resolutionFailureHandler).variants == [toConfig2]

        where:
        // these all map to the same thing
        lhs | rhs
        "*" | "@"
        "*" | "#"
        "%" | "@"
        "%" | "#"
    }

    def "fails when target component does not have matching configurations"() {
        def toId = Stub(ModuleComponentIdentifier) {
            getDisplayName() >> "thing b"
        }
        def toComponent = Stub(IvyComponentGraphResolveState)
        toComponent.id >> toId
        def fromConfig = Stub(ModuleConfigurationMetadata)
        fromConfig.hierarchy >> ImmutableSet.of("from")
        fromConfig.name >> "from"
        toComponent.getConfiguration(_) >> null

        Multimap<String, String> configMapping = LinkedHashMultimap.create()
        configMapping.put(lhs, rhs)

        def failure = Mock(AbstractResolutionFailureException)
        def metadata = new IvyDependencyDescriptor(requested, "12", true, true, false, configMapping, [], [])

        when:
        metadata.selectLegacyConfigurations(fromConfig, toComponent, resolutionFailureHandler)

        then:
        1 * resolutionFailureHandler.externalConfigurationNotFoundFailure(_, _, _) >> failure
        def e = thrown(AbstractResolutionFailureException)
        e == failure

        where:
        lhs    | rhs
        "from" | "to"
        "*"    | "to"
        "%"    | "to"
        "%"    | "to(to)"
    }

    ConfigurationMetadata configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ImmutableSet.copyOf(([name] as Set) + (parents as Set))
        return config
    }

    VariantGraphResolveState configuration(IvyComponentGraphResolveState component, String name) {
        def metadata = Stub(ConfigurationGraphResolveMetadata)
        metadata.visible >> true
        metadata.hierarchy >> ImmutableSet.of(name)

        def variant = Stub(VariantGraphResolveState)
        variant.toString() >> name
        variant.name >> name

        def configuration = Stub(ConfigurationGraphResolveState)
        component.getConfiguration(name) >> configuration
        configuration.name >> name
        configuration.toString() >> name
        configuration.asVariant() >> variant
        configuration.metadata >> metadata
        return variant
    }
}
