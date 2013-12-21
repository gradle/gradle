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

package org.gradle.api.internal.artifacts.metadata

import org.apache.ivy.core.module.descriptor.DefaultDependencyArtifactDescriptor
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor
import org.apache.ivy.core.module.descriptor.DefaultModuleDescriptor
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.component.ProjectComponentSelector
import org.gradle.api.artifacts.component.ComponentSelector
import org.gradle.api.artifacts.component.ModuleComponentSelector
import org.gradle.api.internal.artifacts.DefaultModuleVersionSelector
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.initialization.ProjectAccessListener
import spock.lang.Specification

class DefaultDependencyMetaDataTest extends Specification {
    final requestedModuleId = ModuleRevisionId.newInstance("org", "module", "1.2+")

    def "constructs selector from descriptor"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        expect:
        metaData.requested == DefaultModuleVersionSelector.newSelector("org", "module", "1.2+")
    }

    def "creates a copy with new requested version"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        given:

        when:
        def copy = metaData.withRequestedVersion("1.3+")

        then:
        copy.requested == DefaultModuleVersionSelector.newSelector("org", "module", "1.3+")
        copy.descriptor.dependencyRevisionId == ModuleRevisionId.newInstance("org", "module", "1.3+")
    }

    def "returns this if new requested version is the same as current requested version"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        expect:
        metaData.withRequestedVersion("1.2+").is(metaData)
        metaData.withRequestedVersion(DefaultModuleVersionSelector.newSelector("org", "module", "1.2+")).is(metaData)
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

    def "uses artifacts defined by dependency descriptor"() {
        def descriptor = new DefaultDependencyDescriptor(requestedModuleId, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)
        def fromConfiguration = Stub(ConfigurationMetaData)
        def toConfiguration = Stub(ConfigurationMetaData)

        given:
        fromConfiguration.hierarchy >> (['config', 'super'] as LinkedHashSet)
        descriptor.addDependencyArtifact("config", new DefaultDependencyArtifactDescriptor(descriptor, "art1", "type", "ext", null, [:]))
        descriptor.addDependencyArtifact("other", new DefaultDependencyArtifactDescriptor(descriptor, "art2", "type", "ext", null, [:]))
        descriptor.addDependencyArtifact("super", new DefaultDependencyArtifactDescriptor(descriptor, "art3", "type", "ext", null, [:]))

        expect:
        metaData.getArtifacts(fromConfiguration, toConfiguration)*.name*.name == ['art1', 'art3']
    }

    def "returns a build component selector if descriptor indicates a project dependency"() {
        given:
        def project = Mock(ProjectInternal)
        def projectDependency = new DefaultProjectDependency(project, 'conf1', {} as ProjectAccessListener, true)
        def descriptor = new ProjectDependencyDescriptor(projectDependency, DefaultModuleDescriptor.newDefaultInstance(requestedModuleId), requestedModuleId, false, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        when:
        ComponentSelector componentSelector = metaData.getSelector()

        then:
        1 * project.path >> ':myPath'
        componentSelector instanceof ProjectComponentSelector
        componentSelector.projectPath == ':myPath'
    }

    def "returns a module component selector if descriptor indicates a default dependency"() {
        given:
        def descriptor = new DefaultDependencyDescriptor(DefaultModuleDescriptor.newDefaultInstance(requestedModuleId), requestedModuleId, false, false, false)
        def metaData = new DefaultDependencyMetaData(descriptor)

        when:
        ComponentSelector componentSelector = metaData.getSelector()

        then:
        componentSelector instanceof ModuleComponentSelector
        componentSelector.group == 'org'
        componentSelector.module == 'module'
        componentSelector.version == '1.2+'
    }
}
