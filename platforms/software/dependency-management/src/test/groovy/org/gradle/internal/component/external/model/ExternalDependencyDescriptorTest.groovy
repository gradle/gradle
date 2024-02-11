/*
 * Copyright 2017 the original author or authors.
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
import com.google.common.collect.ImmutableSet
import org.gradle.api.artifacts.VersionConstraint
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.internal.component.model.ComponentArtifactMetadata
import org.gradle.internal.component.model.ComponentGraphResolveState
import org.gradle.internal.component.model.ConfigurationGraphResolveMetadata
import org.gradle.internal.component.model.ConfigurationGraphResolveState
import org.gradle.internal.component.model.ConfigurationMetadata
import org.gradle.internal.component.model.VariantArtifactGraphResolveMetadata
import org.gradle.internal.component.model.VariantGraphResolveState
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentSelector.newSelector

abstract class ExternalDependencyDescriptorTest extends Specification {
    def requested = newSelector(DefaultModuleIdentifier.newId("org", "module"), v("1.2+"))
    def id = DefaultModuleVersionIdentifier.newId("org", "module", "1.2+")

    static VersionConstraint v(String version) {
        new DefaultMutableVersionConstraint(version)
    }

    abstract ExternalDependencyDescriptor create(ModuleComponentSelector selector)

    def "creates a copy with new requested version"() {
        def metadata = create(requested)

        given:

        when:
        def target = newSelector(DefaultModuleIdentifier.newId("org", "module"), v("1.3+"))
        def copy = metadata.withRequested(target)

        then:
        copy != metadata
        copy.selector == target
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

    ConfigurationMetadata configuration(String name, String... parents) {
        def config = Stub(ConfigurationMetadata)
        config.hierarchy >> ImmutableSet.copyOf(([name] as Set) + (parents as Set))
        return config
    }

    VariantGraphResolveState configuration(ComponentGraphResolveState component, String name) {
        return configurationWithHierarchy(component, name, ImmutableSet.of(name))
    }

    VariantGraphResolveState configurationWithArtifacts(ComponentGraphResolveState component, String name) {
        def artifacts = Stub(VariantArtifactGraphResolveMetadata)
        artifacts.artifacts >> ImmutableList.of(Stub(ComponentArtifactMetadata))
        def variant = configuration(component, name)
        variant.resolveArtifacts() >> artifacts
        return variant
    }

    VariantGraphResolveState configurationWithHierarchy(ComponentGraphResolveState component, String name, Set<String> hierarchy) {
        def metadata = Stub(ConfigurationGraphResolveMetadata)
        metadata.visible >> true
        metadata.hierarchy >> hierarchy

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
