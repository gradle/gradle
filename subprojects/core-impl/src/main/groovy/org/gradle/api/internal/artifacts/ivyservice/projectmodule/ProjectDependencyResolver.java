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
package org.gradle.api.internal.artifacts.ivyservice.projectmodule;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.*;

import java.io.File;

public class ProjectDependencyResolver implements DependencyToModuleResolver {
    private final ProjectModuleRegistry projectModuleRegistry;
    private final DependencyToModuleResolver resolver;
    private final ProjectArtifactResolver artifactResolver;

    public ProjectDependencyResolver(ProjectModuleRegistry projectModuleRegistry, DependencyToModuleResolver resolver) {
        this.projectModuleRegistry = projectModuleRegistry;
        this.resolver = resolver;
        artifactResolver = new ProjectArtifactResolver();
    }

    public void resolve(DependencyDescriptor dependencyDescriptor, BuildableModuleVersionResolveResult result) {
        ModuleDescriptor moduleDescriptor = projectModuleRegistry.findProject(dependencyDescriptor);
        if (moduleDescriptor != null) {
            result.resolved(moduleDescriptor.getModuleRevisionId(), moduleDescriptor, artifactResolver);
        } else {
            resolver.resolve(dependencyDescriptor, result);
        }
    }

    private static class ProjectArtifactResolver implements ArtifactResolver {
        public void resolve(Artifact artifact, BuildableArtifactResolveResult result) {
            String path = artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE);
            result.resolved(new File(path));
        }
    }
}
