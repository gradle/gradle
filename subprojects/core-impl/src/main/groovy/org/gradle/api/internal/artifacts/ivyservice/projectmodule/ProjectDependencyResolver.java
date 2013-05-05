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

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor;

import java.io.File;
import java.util.Set;

public class ProjectDependencyResolver implements DependencyToModuleVersionResolver, ModuleToModuleVersionResolver {
    private final ProjectModuleRegistry projectModuleRegistry;
    private final DependencyToModuleVersionResolver resolver;
    private final ProjectArtifactResolver artifactResolver;
    private final ModuleDescriptorConverter moduleDescriptorConverter;

    public ProjectDependencyResolver(ProjectModuleRegistry projectModuleRegistry, DependencyToModuleVersionResolver resolver, ModuleDescriptorConverter moduleDescriptorConverter) {
        this.projectModuleRegistry = projectModuleRegistry;
        this.resolver = resolver;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        artifactResolver = new ProjectArtifactResolver();
    }

    public void resolve(DependencyMetaData dependency, BuildableModuleVersionResolveResult result) {
        DependencyDescriptor descriptor = dependency.getDescriptor();
        if (descriptor instanceof ProjectDependencyDescriptor) {
            ProjectDependencyDescriptor desc = (ProjectDependencyDescriptor) descriptor;
            ModuleDescriptor moduleDescriptor = projectModuleRegistry.findProject(desc);
            ModuleRevisionId moduleRevisionId = moduleDescriptor.getModuleRevisionId();
            DefaultModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
            result.resolved(moduleVersionIdentifier, moduleDescriptor, artifactResolver);
        } else {
            resolver.resolve(dependency, result);
        }
    }

    public void resolve(Module module, Set<? extends Configuration> configurations, BuildableModuleVersionResolveResult result) {
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(configurations, module);
        ModuleRevisionId moduleRevisionId = moduleDescriptor.getModuleRevisionId();
        DefaultModuleVersionIdentifier moduleVersionIdentifier = new DefaultModuleVersionIdentifier(moduleRevisionId.getOrganisation(), moduleRevisionId.getName(), moduleRevisionId.getRevision());
        result.resolved(moduleVersionIdentifier, moduleDescriptor, artifactResolver);
    }

    private static class ProjectArtifactResolver implements ArtifactResolver {
        public void resolve(Artifact artifact, BuildableArtifactResolveResult result) {
            String path = artifact.getExtraAttribute(DefaultIvyDependencyPublisher.FILE_ABSOLUTE_PATH_EXTRA_ATTRIBUTE);
            result.resolved(new File(path));
        }
    }
}
