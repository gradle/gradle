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

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.component.local.model.ProjectDependencyMetaData;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.ModuleToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

import java.util.Set;

public class ProjectDependencyResolver implements DependencyToComponentIdResolver, ModuleToComponentResolver {
    private final ProjectComponentRegistry projectComponentRegistry;
    private final DependencyToComponentIdResolver delegate;
    private final LocalComponentFactory localComponentFactory;

    public ProjectDependencyResolver(ProjectComponentRegistry projectComponentRegistry, LocalComponentFactory localComponentFactory, DependencyToComponentIdResolver delegate) {
        this.projectComponentRegistry = projectComponentRegistry;
        this.delegate = delegate;
        this.localComponentFactory = localComponentFactory;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        if (dependency instanceof ProjectDependencyMetaData) {
            ProjectDependencyMetaData projectDependency = (ProjectDependencyMetaData) dependency;
            LocalComponentMetaData componentMetaData = projectComponentRegistry.getProject(projectDependency.getSelector().getProjectPath());
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
