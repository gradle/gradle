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
import org.gradle.api.artifacts.ClientModule
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class ClientModuleResolverTest extends Specification {
    final ModuleDescriptor module = Mock()
    final ResolveData resolveData = Mock()
    final ClientModuleRegistry clientModuleRegistry = Mock()
    final ModuleRevisionId moduleId = new ModuleRevisionId(new ModuleId("org", "name"), "1.0")
    final ClientModuleResolver resolver = new ClientModuleResolver(clientModuleRegistry)

    def "resolves dependency descriptor that matches module in supplied registry"() {
        DependencyDescriptor dependencyDescriptor = dependency("module")
        when:

        clientModuleRegistry.getClientModule("module") >> module
        def moduleResolver = resolver.create(dependencyDescriptor)

        then:
        moduleResolver.id == moduleId
        moduleResolver.descriptor == module
    }

    def "returns null for unknown module"() {
        DependencyDescriptor dependencyDescriptor = dependency(null)
        
        expect:
        resolver.create(dependencyDescriptor) == null
    }
    
    def dependency(String module) {
        DependencyDescriptor descriptor = Mock()
        _ * descriptor.getDependencyRevisionId() >> moduleId
        _ * descriptor.getExtraAttribute(ClientModule.CLIENT_MODULE_KEY) >> module
        return descriptor
    }
}
