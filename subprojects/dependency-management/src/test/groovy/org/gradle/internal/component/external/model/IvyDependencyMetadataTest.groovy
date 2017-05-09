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
import org.gradle.api.artifacts.ModuleVersionSelector
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.DefaultImmutableModuleIdentifierFactory
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.ModuleExclusions
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.excludes.PatternMatchers
import org.gradle.internal.component.external.descriptor.Artifact
import org.gradle.internal.component.external.descriptor.DefaultExclude
import org.gradle.internal.component.model.ComponentResolveMetadata
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.ConfigurationNotFoundException
import org.gradle.internal.component.model.DefaultDependencyMetadata
import org.gradle.internal.component.model.DefaultDependencyMetadataTest
import org.gradle.internal.component.model.Exclude

class IvyDependencyMetadataTest extends DefaultDependencyMetadataTest {

    @Override
    DefaultDependencyMetadata create(ModuleVersionSelector selector) {
        return new IvyDependencyMetadata(selector, ImmutableListMultimap.of())
    }

    DefaultDependencyMetadata createWithExcludes(ModuleVersionSelector selector, List<Exclude> excludes) {
        return new IvyDependencyMetadata(selector, "12", false, false, true, ImmutableListMultimap.of(), [], excludes)
    }

    @Override
    DefaultDependencyMetadata createWithArtifacts(ModuleVersionSelector selector, List<Artifact> artifacts) {
        return new IvyDependencyMetadata(selector, "12", false, false, true, ImmutableListMultimap.of(), artifacts, [])
    }

    def "excludes nothing when no exclude rules provided"() {
        def dep = createWithExcludes(requested, [])
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(dep.getExcludes(configuration("from").hierarchy)) == ModuleExclusions.excludeNone()
        moduleExclusions.excludeAny(dep.getExcludes(configuration("anything").hierarchy)) == ModuleExclusions.excludeNone()
    }

    def "excludes nothing when traversing a different configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(dep.getExcludes(configuration("anything").hierarchy)) == ModuleExclusions.excludeNone()
    }

    def "applies exclude rules when traversing a configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("from")
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(dep.getExcludes(configuration.hierarchy)) == moduleExclusions.excludeAny(exclude)
    }

    def "applies rules when traversing a child of specified configuration"() {
        def exclude = new DefaultExclude(DefaultModuleIdentifier.newId("group", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude])
        def configuration = configuration("child", "from")
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(dep.getExcludes(configuration.hierarchy)) == moduleExclusions.excludeAny(exclude)
    }

    def "applies matching exclude rules"() {
        def exclude1 = new DefaultExclude(DefaultModuleIdentifier.newId("group1", "*"), ["from"] as String[], PatternMatchers.EXACT)
        def exclude2 = new DefaultExclude(DefaultModuleIdentifier.newId("group2", "*"), ["*"] as String[], PatternMatchers.EXACT)
        def exclude3 = new DefaultExclude(DefaultModuleIdentifier.newId("group3", "*"), ["other"] as String[], PatternMatchers.EXACT)
        def dep = createWithExcludes(requested, [exclude1, exclude2, exclude3])
        def configuration = configuration("from")
        def moduleExclusions = new ModuleExclusions(new DefaultImmutableModuleIdentifierFactory())

        expect:
        moduleExclusions.excludeAny(dep.getExcludes(configuration.hierarchy)) == moduleExclusions.excludeAny(exclude1, exclude2)
    }

    def "selects no configurations when no configuration mappings provided"() {
        def fromComponent = Stub(ComponentResolveMetadata)
        def toComponent = Stub(ComponentResolveMetadata)
        def fromConfig = Stub(ConfigurationMetadata)
        fromConfig.name >> "from"

        expect:
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, ImmutableListMultimap.of(), [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema).empty
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1, toConfig2] // verify order as well
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1, toConfig2]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1, toConfig2]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent, attributesSchema) as List == [toConfig2]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1, toConfig2]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent, attributesSchema) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig3, toComponent, attributesSchema) as List == [toConfig2]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1, toConfig3]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent, attributesSchema) as List == [toConfig1, toConfig3]
        metadata.selectConfigurations(fromComponent, fromConfig3, toComponent, attributesSchema) as List == [toConfig2, toConfig3]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1, toConfig2]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent, attributesSchema) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig3, toComponent, attributesSchema) as List == [toConfig2]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent, attributesSchema) as List == [toConfig1]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent, attributesSchema) as List == [toConfig2]
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
        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema) as List == [toConfig1]
        metadata.selectConfigurations(fromComponent, fromConfig2, toComponent, attributesSchema) as List == [toConfig2]

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

        def metadata = new IvyDependencyMetadata(requested, "12", true, true, true, configMapping, [], [])

        when:
        metadata.selectConfigurations(fromComponent, fromConfig, toComponent, attributesSchema)

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
}
