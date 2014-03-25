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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.ivyservice.BuildableComponentResolveResult;
import org.gradle.api.internal.artifacts.ivyservice.DependencyToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.api.internal.artifacts.ivyservice.ModuleToModuleVersionResolver;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor;
import org.gradle.api.internal.artifacts.metadata.DependencyMetaData;
import org.gradle.api.internal.artifacts.metadata.LocalComponentMetaData;

import java.util.Set;

public class ProjectDependencyResolver implements DependencyToModuleVersionResolver, ModuleToModuleVersionResolver {
    private final ProjectComponentRegistry projectComponentRegistry;
    private final DependencyToModuleVersionResolver delegate;
    private final LocalComponentFactory localComponentFactory;

    public ProjectDependencyResolver(ProjectComponentRegistry projectComponentRegistry, LocalComponentFactory localComponentFactory, DependencyToModuleVersionResolver delegate) {
        this.projectComponentRegistry = projectComponentRegistry;
        this.delegate = delegate;
        this.localComponentFactory = localComponentFactory;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentResolveResult result) {
        DependencyDescriptor descriptor = dependency.getDescriptor();
        if (descriptor instanceof ProjectDependencyDescriptor) {
            ProjectDependencyDescriptor desc = (ProjectDependencyDescriptor) descriptor;
            LocalComponentMetaData componentMetaData = projectComponentRegistry.getProject(desc.getTargetProject().getPath());
            result.resolved(componentMetaData.toResolveMetaData());
        } else {
            delegate.resolve(dependency, result);
        }
    }

    public void resolve(ModuleInternal module, Set<? extends Configuration> configurations, BuildableComponentResolveResult result) {
        LocalComponentMetaData componentMetaData = localComponentFactory.convert(configurations, module);
        result.resolved(componentMetaData.toResolveMetaData());
    }
}
