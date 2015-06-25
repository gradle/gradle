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

import org.gradle.api.artifacts.ResolveContext;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;
import org.gradle.api.internal.artifacts.ivyservice.LocalComponentFactory;
import org.gradle.internal.component.local.model.DefaultProjectComponentSelector;
import org.gradle.internal.component.local.model.LocalComponentMetaData;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.DependencyMetaData;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.resolver.ResolveContextToComponentResolver;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;

public class ProjectDependencyResolver implements DependencyToComponentIdResolver, ResolveContextToComponentResolver, ComponentMetaDataResolver {
    private final ProjectComponentRegistry projectComponentRegistry;
    private final DependencyToComponentIdResolver delegateIdResolver;
    private final ComponentMetaDataResolver delegateComponentResolver;
    private final LocalComponentFactory localComponentFactory;

    public ProjectDependencyResolver(ProjectComponentRegistry projectComponentRegistry, LocalComponentFactory localComponentFactory, DependencyToComponentIdResolver delegateIdResolver, ComponentMetaDataResolver delegateComponentResolver) {
        this.projectComponentRegistry = projectComponentRegistry;
        this.delegateIdResolver = delegateIdResolver;
        this.delegateComponentResolver = delegateComponentResolver;
        this.localComponentFactory = localComponentFactory;
    }

    public void resolve(DependencyMetaData dependency, BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof ProjectComponentSelector) {
            ProjectComponentSelector selector = (ProjectComponentSelector) dependency.getSelector();
            String projectPath = selector.getProjectPath();
            LocalComponentMetaData componentMetaData = projectComponentRegistry.getProject(projectPath);
            if (componentMetaData == null) {
                result.failed(new ModuleVersionResolveException(selector, "project '" + projectPath + "' not found."));
            } else {
                result.resolved(componentMetaData.toResolveMetaData());
            }
        } else {
            delegateIdResolver.resolve(dependency, result);
        }
    }

    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (identifier instanceof ProjectComponentIdentifier) {
            String projectPath = ((ProjectComponentIdentifier) identifier).getProjectPath();
            LocalComponentMetaData componentMetaData = projectComponentRegistry.getProject(projectPath);
            if (componentMetaData == null) {
                result.failed(new ModuleVersionResolveException(new DefaultProjectComponentSelector(projectPath), "project '" + projectPath + "' not found."));
            } else {
                result.resolved(componentMetaData.toResolveMetaData());
            }
        } else {
            delegateComponentResolver.resolve(identifier, componentOverrideMetadata, result);
        }
    }

    public void resolve(ResolveContext resolveContext, BuildableComponentResolveResult result) {
        LocalComponentMetaData componentMetaData = localComponentFactory.convert(resolveContext);
        result.resolved(componentMetaData.toResolveMetaData());
    }
}
