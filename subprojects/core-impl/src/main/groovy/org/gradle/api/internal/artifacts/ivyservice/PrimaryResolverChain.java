/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice;

import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Resolver which looks for definitions first in defined Client Modules, before delegating to the user-defined resolver chain.
 * Artifact download is delegated to user-defined resolver chain.
 */
public class PrimaryResolverChain implements DependencyToModuleResolver, ArtifactToFileResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(PrimaryResolverChain.class);
    private final DependencyToModuleResolver clientModuleResolver;
    private final GradleDependencyResolver projectResolver;
    private final DependencyToModuleResolver ivyDependencyResolver;
    private final ArtifactToFileResolver ivyArtifactResolver;

    public PrimaryResolverChain(DependencyToModuleResolver clientModuleResolver, GradleDependencyResolver projectResolver, DependencyToModuleResolver ivyDependencyResolver, ArtifactToFileResolver ivyArtifactResolver) {
        this.clientModuleResolver = clientModuleResolver;
        this.projectResolver = projectResolver;
        this.ivyDependencyResolver = ivyDependencyResolver;
        this.ivyArtifactResolver = ivyArtifactResolver;
    }

    public ModuleVersionResolver create(DependencyDescriptor dependencyDescriptor) {
        ModuleVersionResolver clientModuleVersionResolver = clientModuleResolver.create(dependencyDescriptor);
        if (clientModuleVersionResolver != null) {
            LOGGER.debug("Found client module: {}", clientModuleVersionResolver.getId());
            return clientModuleVersionResolver;
        }
        ModuleVersionResolver projectModuleVersionResolver = projectResolver.create(dependencyDescriptor);
        if (projectModuleVersionResolver != null) {
            LOGGER.debug("Found project module: {}", projectModuleVersionResolver.getId());
            return projectModuleVersionResolver;
        }
        return ivyDependencyResolver.create(dependencyDescriptor);
    }

    public File resolve(Artifact artifact) {
        File projectFile = projectResolver.resolve(artifact);
        if (projectFile != null) {
            return projectFile;
        }

        return ivyArtifactResolver.resolve(artifact);
    }
}
