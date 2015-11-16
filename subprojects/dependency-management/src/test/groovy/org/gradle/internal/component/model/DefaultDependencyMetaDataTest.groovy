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

import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.ivyservice.IvyUtil
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentSelector
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector
import spock.lang.Specification

class DefaultDependencyMetaDataTest extends Specification {
    final requestedModuleId = IvyUtil.createModuleRevisionId("org", "module", "1.2+")

    def "constructs meta-data from ivy descriptor"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        expect:
        metaData.requested == DefaultModuleVersionSelector.newSelector("org", "module", "1.2+")
    }

    def "constructs meta-data from component id"() {
        def id = new DefaultModuleComponentIdentifier("org", "module", "1.1")
        def metaData = new DefaultDependencyMetaData(id)

        expect:
        metaData.descriptor.dependencyRevisionId == IvyUtil.createModuleRevisionId("org", "module", "1.1")
        metaData.requested == DefaultModuleVersionSelector.newSelector("org", "module", "1.1")
    }

    def "constructs meta-data from module version id"() {
        def id = new DefaultModuleVersionIdentifier("org", "module", "1.1")
        def metaData = new DefaultDependencyMetaData(id)

        expect:
        metaData.descriptor.dependencyRevisionId == IvyUtil.createModuleRevisionId("org", "module", "1.1")
        metaData.requested == DefaultModuleVersionSelector.newSelector("org", "module", "1.1")
    }

    def "creates a copy with new requested version"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        given:

        when:
        def copy = metaData.withRequestedVersion("1.3+")

        then:
        copy.requested == DefaultModuleVersionSelector.newSelector("org", "module", "1.3+")
        copy.descriptor.dependencyRevisionId == IvyUtil.createModuleRevisionId("org", "module", "1.3+")
    }

    def "returns this if new requested version is the same as current requested version"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        expect:
        metaData.withRequestedVersion("1.2+").is(metaData)
        metaData.withTarget(DefaultModuleComponentSelector.newSelector("org", "module", "1.2+")).is(metaData)
    }

    def "can set changing flag"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        when:
        def copy = metaData.withChanging()

        then:
        copy.descriptor.dependencyRevisionId == requestedModuleId
        copy.descriptor.changing
    }

    def "returns this when changing is already true"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, true)
        def metaData = new DefaultDependencyMetaData(descriptor)

        expect:
        metaData.withChanging().is(metaData)
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)
        def fromConfiguration = Stub(ConfigurationMetaData)
        def toConfiguration = Stub(ConfigurationMetaData)

        expect:
        metaData.getArtifacts(fromConfiguration, toConfiguration).empty
    }

    def "returns empty set of artifacts when dependency descriptor does not declare any artifacts for source configuration"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)
        def fromConfiguration = Stub(ConfigurationMetaData)
        def toConfiguration = Stub(ConfigurationMetaData)

        given:
        descriptor.addDependencyArtifact("other", new DefaultDependencyArtifactDescriptor(descriptor, "art", "type", "ext", null, [:]))

        expect:
        metaData.getArtifacts(fromConfiguration, toConfiguration).empty
    }

    def "uses artifacts defined by dependency descriptor for specified source and target configurations "() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)
        def fromConfiguration = Stub(ConfigurationMetaData)
        def targetComponent = Stub(ComponentResolveMetaData)
        def toConfiguration = Stub(ConfigurationMetaData)
        def artifact1 = Stub(ComponentArtifactMetaData)
        def artifact2 = Stub(ComponentArtifactMetaData)

        given:
        fromConfiguration.hierarchy >> (['config', 'super'] as LinkedHashSet)
        toConfiguration.component >> targetComponent
        descriptor.addDependencyArtifact("config", new DefaultDependencyArtifactDescriptor(descriptor, "art1", "type", "ext", null, [:]))
        descriptor.addDependencyArtifact("other", new DefaultDependencyArtifactDescriptor(descriptor, "art2", "type", "ext", null, [:]))
        descriptor.addDependencyArtifact("super", new DefaultDependencyArtifactDescriptor(descriptor, "art3", "type", "ext", null, [:]))
        toConfiguration.artifact({it.name == 'art1'}) >> artifact1
        toConfiguration.artifact({it.name == 'art3'}) >> artifact2

        expect:
        metaData.getArtifacts(fromConfiguration, toConfiguration) == [artifact1, artifact2] as Set
    }

    def "uses artifacts defined by dependency descriptor"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        given:
        descriptor.addDependencyArtifact("config", new DefaultDependencyArtifactDescriptor(descriptor, "art1", "type", "ext", null, [:]))
        descriptor.addDependencyArtifact("other", new DefaultDependencyArtifactDescriptor(descriptor, "art2", "type", "ext", null, [:]))
        descriptor.addDependencyArtifact("super", new DefaultDependencyArtifactDescriptor(descriptor, "art3", "type", "ext", null, [:]))

        expect:
        metaData.artifacts.size() == 3
        def artifacts = metaData.artifacts as List
        artifacts[0].name == 'art1'
        artifacts[1].name == 'art2'
        artifacts[2].name == 'art3'
    }

    def "returns a module component selector if descriptor indicates a default dependency"() {
        given:
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        when:
        ComponentSelector componentSelector = metaData.getSelector()

        then:
        componentSelector instanceof ModuleComponentSelector
        componentSelector.group == 'org'
        componentSelector.module == 'module'
        componentSelector.version == '1.2+'
    }

    def "retains transitive and changing flags in substituted dependency"() {
        given:
        def requestedModuleDescriptor = DefaultModuleDescriptor.newDefaultInstance(requestedModuleId)
        def descriptor = new DefaultDependencyDescriptor(requestedModuleDescriptor, requestedModuleId, false, changing, transitive)
        def metaData = new DefaultDependencyMetaData(descriptor)

        when:
        DependencyMetaData replacedMetaData = metaData.withTarget(DefaultProjectComponentSelector.newSelector("test"))

        then:
        replacedMetaData.getSelector() instanceof ProjectComponentSelector
        replacedMetaData.isTransitive() == metaData.isTransitive()
        replacedMetaData.isChanging() == metaData.isChanging()

        where:
        transitive | changing
        true       | true
        false      | true
        true       | false
        false      | false
    }
}
