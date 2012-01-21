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
import org.apache.ivy.core.resolve.ResolveData
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveResult
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ClientModuleDependencyDescriptor
import spock.lang.Specification
import org.gradle.api.internal.artifacts.ivyservice.ModuleVersionResolveException

/**
 * @author Hans Dockter
 */
class ClientModuleResolverTest extends Specification {
    final ModuleDescriptor module = Mock()
    final ResolveData resolveData = Mock()
    final ModuleRevisionId moduleId = new ModuleRevisionId(new ModuleId("org", "name"), "1.0")
    final DependencyToModuleResolver target = Mock()
    final ModuleVersionResolveResult resolvedModule = Mock()
    final ClientModuleResolver resolver = new ClientModuleResolver(target)

    def "replaces meta-data for a client module dependency"() {
        ClientModuleDependencyDescriptor dependencyDescriptor = Mock()

        when:
        def resolveResult = resolver.resolve(dependencyDescriptor)

        then:
        1 * target.resolve(dependencyDescriptor) >> resolvedModule
        _ * dependencyDescriptor.targetModule >> module

        and:
        resolveResult.descriptor == module
        resolveResult.failure == null
        resolveResult.id == module.moduleRevisionId
    }

    def "does not replace meta-data for unknown module version"() {
        DependencyDescriptor dependencyDescriptor = Mock()
        
        when:
        def resolveResult = resolver.resolve(dependencyDescriptor)

        then:
        1 * target.resolve(dependencyDescriptor) >> resolvedModule

        and:
        resolveResult == resolvedModule
    }

    def "does not replace meta-data for broken module version"() {
        ClientModuleDependencyDescriptor dependencyDescriptor = Mock()

        given:
        resolvedModule.failure >> new ModuleVersionResolveException("broken")

        when:
        def resolveResult = resolver.resolve(dependencyDescriptor)

        then:
        1 * target.resolve(dependencyDescriptor) >> resolvedModule

        and:
        resolveResult == resolvedModule
    }
}
