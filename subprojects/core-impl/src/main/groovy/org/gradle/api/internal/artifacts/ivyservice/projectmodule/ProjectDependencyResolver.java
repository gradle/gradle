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
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ArtifactResolveException;

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

    public ModuleVersionResolveResult resolve(DependencyDescriptor dependencyDescriptor) {
        ModuleDescriptor moduleDescriptor = projectModuleRegistry.findProject(dependencyDescriptor);

        if (moduleDescriptor == null) {
            return resolver.resolve(dependencyDescriptor);
        }

        return new ProjectDependencyModuleVersionResolveResult(moduleDescriptor, artifactResolver);
    }

    private static class ProjectArtifactResolver implements ArtifactResolver {
        public ArtifactResolveResult resolve(Artifact artifact) throws ArtifactResolveException {
            String path = artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE);
            return new FileBackedArtifactResolveResult(new File(path));
        }
    }

    private static class ProjectDependencyModuleVersionResolveResult implements ModuleVersionResolveResult {
        private final ModuleDescriptor moduleDescriptor;
        private final ArtifactResolver artifactResolver;

        public ProjectDependencyModuleVersionResolveResult(ModuleDescriptor moduleDescriptor, ArtifactResolver artifactResolver) {
            this.moduleDescriptor = moduleDescriptor;
            this.artifactResolver = artifactResolver;
        }

        public ModuleVersionResolveException getFailure() {
            return null;
        }

        public ModuleRevisionId getId() throws ModuleVersionResolveException {
            return moduleDescriptor.getModuleRevisionId();
        }

        public ModuleDescriptor getDescriptor() throws ModuleVersionResolveException {
            return moduleDescriptor;
        }

        public ArtifactResolver getArtifactResolver() throws ModuleVersionResolveException {
            return artifactResolver;
        }
    }
}
