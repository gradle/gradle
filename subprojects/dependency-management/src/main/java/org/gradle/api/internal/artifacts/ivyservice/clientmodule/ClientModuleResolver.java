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

package org.gradle.api.internal.artifacts.ivyservice.clientmodule;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetaData;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetaData;
import org.gradle.internal.component.local.model.DslOriginDependencyMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import java.util.List;

public class ClientModuleResolver implements ComponentMetaDataResolver {
    private final ComponentMetaDataResolver resolver;
    private final DependencyDescriptorFactory dependencyDescriptorFactory;

    public ClientModuleResolver(ComponentMetaDataResolver resolver, DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.resolver = resolver;
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }

    public void resolve(DependencyMetaData dependency, ComponentIdentifier identifier, BuildableComponentResolveResult result) {
        resolver.resolve(dependency, identifier, result);

        if (result.getFailure() != null) {
            return;
        }
        if (dependency instanceof DslOriginDependencyMetaData) {
            ModuleDependency maybeClientModule = ((DslOriginDependencyMetaData) dependency).getSource();
            if (maybeClientModule instanceof ClientModule) {
                ClientModule clientModule = (ClientModule) maybeClientModule;

                MutableModuleComponentResolveMetaData clientModuleMetaData = ((MutableModuleComponentResolveMetaData)result.getMetaData()).copy();
                addClientModuleDependencies(clientModule, clientModuleMetaData);

                setClientModuleArtifact(clientModuleMetaData);

                result.setMetaData(clientModuleMetaData);
            }
        }
    }

    private void addClientModuleDependencies(ClientModule clientModule, MutableModuleComponentResolveMetaData clientModuleMetaData) {
        List<DependencyMetaData> dependencies = Lists.newArrayList();
        for (ModuleDependency moduleDependency : clientModule.getDependencies()) {
            DependencyMetaData dependencyMetaData = dependencyDescriptorFactory.createDependencyDescriptor(moduleDependency.getConfiguration(), clientModuleMetaData.getDescriptor(), moduleDependency);
            dependencies.add(dependencyMetaData);
        }
        clientModuleMetaData.setDependencies(dependencies);
    }

    private void setClientModuleArtifact(MutableModuleComponentResolveMetaData clientModuleMetaData) {
        ModuleComponentArtifactMetaData artifact = clientModuleMetaData.artifact("jar", "jar", null);
        clientModuleMetaData.setArtifacts(Sets.newHashSet(artifact));
    }
}
