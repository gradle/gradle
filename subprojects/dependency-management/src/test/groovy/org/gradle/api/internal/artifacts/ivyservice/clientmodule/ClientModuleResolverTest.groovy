/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.clientmodule

import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata
import org.gradle.internal.component.local.model.DslOriginDependencyMetadata
import org.gradle.internal.component.model.ComponentOverrideMetadata
import org.gradle.internal.resolve.ModuleVersionResolveException
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver
import org.gradle.internal.resolve.result.BuildableComponentResolveResult
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class ClientModuleResolverTest extends Specification {
    final target = Mock(ComponentMetaDataResolver)
    final dependencyDescriptorFactory = Mock(DependencyDescriptorFactory)
    final ClientModuleResolver resolver = new ClientModuleResolver(target, dependencyDescriptorFactory)

    def id = Mock(ComponentIdentifier)
    def result = Mock(BuildableComponentResolveResult)
    def metaData = Mock(ModuleComponentResolveMetadata)
    def mutableMetaData = Mock(MutableModuleComponentResolveMetadata)
    def updatedMetaData = Mock(ModuleComponentResolveMetadata)
    def componentRequestMetaData = Mock(ComponentOverrideMetadata)
    def dependency = Mock(DslOriginDependencyMetadata)

    def "replaces meta-data for a client module dependency"() {
        def clientModule = Mock(ClientModule)
        def dep = Mock(ModuleDependency)
        def dependencyMetaData = Mock(DslOriginDependencyMetadata)
        def artifact = Mock(ModuleComponentArtifactMetadata)

        when:
        resolver.resolve(id, componentRequestMetaData, result)

        then:
        1 * target.resolve(id, componentRequestMetaData, result)
        1 * result.getFailure() >> null
        1 * componentRequestMetaData.clientModule >> clientModule
        1 * result.getMetaData() >> metaData
        1 * metaData.asMutable() >> mutableMetaData
        1 * clientModule.getDependencies() >> ([dep] as Set)
        1 * dep.getTargetConfiguration() >> "config"
        1 * dependencyDescriptorFactory.createDependencyDescriptor("config", null, dep) >> dependencyMetaData
        1 * mutableMetaData.setDependencies([dependencyMetaData])
        1 * mutableMetaData.artifact('jar', 'jar', null) >> artifact
        1 * mutableMetaData.setArtifacts({
            (it as List) == [artifact]
        })
        1 * mutableMetaData.asImmutable() >> updatedMetaData
        1 * result.setMetaData(updatedMetaData)
        0 * _
    }

    def "does not replace meta-data when not client module"() {
        when:
        resolver.resolve(id, componentRequestMetaData, result)

        then:
        1 * target.resolve(id, componentRequestMetaData, result)
        1 * result.getFailure() >> null
        1 * componentRequestMetaData.clientModule >> null
        0 * _
    }

    def "does not replace meta-data for broken module version"() {
        when:
        resolver.resolve(id, componentRequestMetaData, result)

        then:
        1 * target.resolve(id, componentRequestMetaData, result)
        _ * result.failure >> new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        0 * _
    }
}
