/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.clientmodule;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.BuildableModuleVersionResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleResolver;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ClientModuleDependencyDescriptor;

/**
 * @author Hans Dockter
 */
public class ClientModuleResolver implements DependencyToModuleResolver {
    private final DependencyToModuleResolver resolver;

    public ClientModuleResolver(DependencyToModuleResolver resolver) {
        this.resolver = resolver;
    }

    public void resolve(DependencyMetaData dependency, BuildableModuleVersionResolveResult result) {
        resolver.resolve(dependency, result);

        DependencyDescriptor descriptor = dependency.getDescriptor();
        if (result.getFailure() != null || !(descriptor instanceof ClientModuleDependencyDescriptor)) {
            return;
        }

        ClientModuleDependencyDescriptor clientModuleDependencyDescriptor = (ClientModuleDependencyDescriptor) descriptor;
        ModuleDescriptor moduleDescriptor = clientModuleDependencyDescriptor.getTargetModule();

        result.setMetaData(moduleDescriptor);
    }
}
