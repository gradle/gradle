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
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
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

    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        resolver.resolve(identifier, componentOverrideMetadata, result);

        if (result.getFailure() != null) {
            return;
        }
        ClientModule clientModule = componentOverrideMetadata.getClientModule();
        if (clientModule != null) {
            MutableModuleComponentResolveMetadata clientModuleMetaData = ((ModuleComponentResolveMetadata)result.getMetaData()).asMutable();
            addClientModuleDependencies(clientModule, clientModuleMetaData);

            setClientModuleArtifact(clientModuleMetaData);

            result.setMetaData(clientModuleMetaData.asImmutable());
        }
    }

    private void addClientModuleDependencies(ClientModule clientModule, MutableModuleComponentResolveMetadata clientModuleMetaData) {
        List<DependencyMetadata> dependencies = Lists.newArrayList();
        for (ModuleDependency moduleDependency : clientModule.getDependencies()) {
            DependencyMetadata dependencyMetadata = dependencyDescriptorFactory.createDependencyDescriptor(moduleDependency.getTargetConfiguration(), null, moduleDependency);
            dependencies.add(dependencyMetadata);
        }
        clientModuleMetaData.setDependencies(dependencies);
    }

    private void setClientModuleArtifact(MutableModuleComponentResolveMetadata clientModuleMetaData) {
        ModuleComponentArtifactMetadata artifact = clientModuleMetaData.artifact("jar", "jar", null);
        clientModuleMetaData.setArtifacts(Sets.newHashSet(artifact));
    }
}
