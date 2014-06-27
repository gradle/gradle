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
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ClientModule;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.internal.artifacts.ivyservice.BuildableComponentResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.EnhancedDependencyDescriptor;
import org.gradle.api.internal.artifacts.metadata.ComponentMetaData;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.metadata.MutableModuleVersionMetaData;
import org.gradle.internal.Transformers;

import java.util.List;

public class ClientModuleResolver implements DependencyToModuleVersionResolver {
    private final DependencyToModuleVersionResolver resolver;
    private final DependencyDescriptorFactory dependencyDescriptorFactory;
    private final Transformer<MutableModuleVersionMetaData, ComponentMetaData> toModuleVersionMetaData = Transformers.cast(MutableModuleVersionMetaData.class);

    public ClientModuleResolver(DependencyToModuleVersionResolver resolver, DependencyDescriptorFactory dependencyDescriptorFactory) {
        this.resolver = resolver;
        this.dependencyDescriptorFactory = dependencyDescriptorFactory;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentResolveResult result) {
        resolver.resolve(dependency, result);

        if (result.getFailure() != null) {
            return;
        }
        DependencyDescriptor descriptor = dependency.getDescriptor();
        if (descriptor instanceof EnhancedDependencyDescriptor) {
            ModuleDependency maybeClientModule = ((EnhancedDependencyDescriptor) descriptor).getModuleDependency();
            if (maybeClientModule instanceof ClientModule) {
                ClientModule clientModule = (ClientModule) maybeClientModule;

                MutableModuleVersionMetaData clientModuleMetaData = toModuleVersionMetaData.transform(result.getMetaData()).copy();
                addClientModuleDependencies(clientModule, clientModuleMetaData);

                setClientModuleArtifact(clientModuleMetaData);

                result.setMetaData(clientModuleMetaData);
            }
        }
    }

    private void addClientModuleDependencies(ClientModule clientModule, MutableModuleVersionMetaData clientModuleMetaData) {
        List<DependencyMetaData> dependencies = Lists.newArrayList();
        for (ModuleDependency moduleDependency : clientModule.getDependencies()) {
            DependencyMetaData dependencyMetaData = dependencyDescriptorFactory.createDependencyDescriptor(moduleDependency.getConfiguration(), clientModuleMetaData.getDescriptor(), moduleDependency);
            dependencies.add(dependencyMetaData);
        }
        clientModuleMetaData.setDependencies(dependencies);
    }

    private void setClientModuleArtifact(MutableModuleVersionMetaData clientModuleMetaData) {
        ModuleVersionArtifactMetaData artifact = clientModuleMetaData.artifact("jar", "jar", null);
        clientModuleMetaData.setArtifacts(Sets.newHashSet(artifact));
    }
}
