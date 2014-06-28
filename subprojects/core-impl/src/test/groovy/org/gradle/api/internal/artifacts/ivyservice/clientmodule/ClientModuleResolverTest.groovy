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

import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.gradle.api.artifacts.ClientModule
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.api.internal.artifacts.ivyservice.BuildableComponentResolveResult
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

class ClientModuleResolverTest extends Specification {
    final target = Mock(DependencyToModuleVersionResolver)
    final dependencyDescriptorFactory = Mock(DependencyDescriptorFactory)
    final ClientModuleResolver resolver = new ClientModuleResolver(target, dependencyDescriptorFactory)

    def result = Mock(BuildableComponentResolveResult)
    def metaData = Mock(MutableModuleVersionMetaData)
    def descriptor = Mock(EnhancedDependencyDescriptor)
    def dependency = Mock(DependencyMetaData) {
        getDescriptor() >> descriptor
    }

    def "replaces meta-data for a client module dependency"() {
        def clientModule = Mock(ClientModule)
        def dep = Mock(ModuleDependency)
        def moduleDescriptor = Mock(ModuleDescriptor)
        def dependencyMetaData = Mock(DependencyMetaData)
        def artifact = Mock(ModuleVersionArtifactMetaData)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * target.resolve(dependency, result)
        1 * result.getFailure() >> null
        1 * dependency.descriptor >> descriptor
        1 * descriptor.moduleDependency >> clientModule
        1 * result.getMetaData() >> metaData
        1 * metaData.copy() >> metaData
        1 * clientModule.getDependencies() >> ([dep] as Set)
        1 * dep.getConfiguration() >> "config"
        1 * metaData.getDescriptor() >> moduleDescriptor
        1 * dependencyDescriptorFactory.createDependencyDescriptor("config", moduleDescriptor, dep) >> dependencyMetaData
        1 * metaData.setDependencies([dependencyMetaData])
        1 * metaData.artifact('jar', 'jar', null) >> artifact
        1 * metaData.setArtifacts({
            (it as List) == [artifact]
        })
        1 * result.setMetaData(metaData)
        0 * _
    }

    def "does not replace meta-data when not client module"() {
        def descriptor = Mock(EnhancedDependencyDescriptor)
        def moduleDependency = Mock(ModuleDependency)

        when:
        resolver.resolve(dependency, result)

        then:
        1 * target.resolve(dependency, result)
        1 * result.getFailure() >> null
        1 * dependency.descriptor >> descriptor
        1 * descriptor.moduleDependency >> moduleDependency
        0 * _
    }

    def "does not replace meta-data for broken module version"() {
        when:
        resolver.resolve(dependency, result)

        then:
        1 * target.resolve(dependency, result)
        _ * result.failure >> new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        0 * _
    }
}
