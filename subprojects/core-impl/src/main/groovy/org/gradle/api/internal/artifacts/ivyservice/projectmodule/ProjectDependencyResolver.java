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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.DefaultArtifactIdentifier;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.DependencyMetaData;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.ProjectDependencyDescriptor;

import java.io.File;
import java.util.Map;
import java.util.Set;

public class ProjectDependencyResolver implements DependencyToModuleVersionResolver, ModuleToModuleVersionResolver {
    private final ProjectModuleRegistry projectModuleRegistry;
    private final DependencyToModuleVersionResolver resolver;
    private final ModuleDescriptorConverter moduleDescriptorConverter;

    public ProjectDependencyResolver(ProjectModuleRegistry projectModuleRegistry, DependencyToModuleVersionResolver resolver, ModuleDescriptorConverter moduleDescriptorConverter) {
        this.projectModuleRegistry = projectModuleRegistry;
        this.resolver = resolver;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
    }

    public void resolve(DependencyMetaData dependency, BuildableModuleVersionResolveResult result) {
        DependencyDescriptor descriptor = dependency.getDescriptor();
        if (descriptor instanceof ProjectDependencyDescriptor) {
            ProjectDependencyDescriptor desc = (ProjectDependencyDescriptor) descriptor;
            ModuleVersionPublishMetaData publishMetaData = projectModuleRegistry.findProject(desc);
            ModuleDescriptor moduleDescriptor = publishMetaData.getModuleDescriptor();
            ModuleRevisionId moduleRevisionId = moduleDescriptor.getModuleRevisionId();
            ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(moduleRevisionId);
            result.resolved(moduleVersionIdentifier, moduleDescriptor, new ProjectArtifactResolver(publishMetaData));
        } else {
            resolver.resolve(dependency, result);
        }
    }

    public void resolve(Module module, Set<? extends Configuration> configurations, BuildableModuleVersionResolveResult result) {
        ModuleVersionPublishMetaData publishMetaData = moduleDescriptorConverter.convert(configurations, module);
        ModuleDescriptor moduleDescriptor = publishMetaData.getModuleDescriptor();
        ModuleRevisionId moduleRevisionId = moduleDescriptor.getModuleRevisionId();
        ModuleVersionIdentifier moduleVersionIdentifier = DefaultModuleVersionIdentifier.newId(moduleRevisionId);
        result.resolved(moduleVersionIdentifier, moduleDescriptor, new ProjectArtifactResolver(publishMetaData));
    }

    private static class ProjectArtifactResolver implements ArtifactResolver {
        private final ModuleVersionPublishMetaData publishMetaData;

        public ProjectArtifactResolver(ModuleVersionPublishMetaData publishMetaData) {
            this.publishMetaData = publishMetaData;
        }

        public void resolve(Artifact artifact, BuildableArtifactResolveResult result) {
            for (Map.Entry<Artifact, File> entry : publishMetaData.getArtifacts().entrySet()) {
                if (entry.getKey().getId().equals(artifact.getId())) {
                    result.resolved(entry.getValue());
                    return;
                }
            }
            result.notFound(new DefaultArtifactIdentifier(artifact));
        }
    }
}
