/*
 * Copyright 2007-2009 the original author or authors.
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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor
import org.apache.ivy.core.module.descriptor.ModuleDescriptor
import org.apache.ivy.core.module.id.ModuleId
import org.apache.ivy.core.module.id.ModuleRevisionId
import org.gradle.api.artifacts.ModuleVersionIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ClientModuleDependencyDescriptor
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.DefaultModuleVersionSelector.newSelector

/**
 * @author Hans Dockter
 */
class ClientModuleResolverTest extends Specification {
    final ModuleDescriptor module = Mock()
    final ModuleRevisionId moduleId = new ModuleRevisionId(new ModuleId("org", "name"), "1.0")
    final ModuleVersionIdentifier moduleVersionId = new DefaultModuleVersionIdentifier("org", "name", "1.0")
    final DependencyToModuleResolver target = Mock()
    final ClientModuleResolver resolver = new ClientModuleResolver(target)

    def "replaces meta-data for a client module dependency"() {
        ClientModuleDependencyDescriptor dependencyDescriptor = Mock()
        DependencyMetaData dependencyMetaData = Mock()
        BuildableModuleVersionResolveResult result = Mock()

        given:
        _ * dependencyMetaData.descriptor >> dependencyDescriptor
        _ * dependencyDescriptor.targetModule >> module
        _ * module.moduleRevisionId >> moduleId

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        1 * target.resolve(dependencyMetaData, result)
        1 * result.setMetaData(moduleVersionId, module)
        _ * result.failure >> null
        0 * result._
    }

    def "does not replace meta-data for unknown module version"() {
        DependencyDescriptor dependencyDescriptor = Mock()
        DependencyMetaData dependencyMetaData = Mock()
        BuildableModuleVersionResolveResult result = Mock()

        given:
        _ * dependencyMetaData.descriptor >> dependencyDescriptor

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        1 * target.resolve(dependencyMetaData, result)
        _ * result.failure >> null
        0 * result._
    }

    def "does not replace meta-data for broken module version"() {
        ClientModuleDependencyDescriptor dependencyDescriptor = Mock()
        DependencyMetaData dependencyMetaData = Mock()
        BuildableModuleVersionResolveResult result = Mock()

        given:
        _ * dependencyMetaData.descriptor >> dependencyDescriptor

        when:
        resolver.resolve(dependencyMetaData, result)

        then:
        1 * target.resolve(dependencyMetaData, result)
        _ * result.failure >> new ModuleVersionResolveException(newSelector("a", "b", "c"), "broken")
        0 * result._
    }
}
