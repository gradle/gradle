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

import org.apache.ivy.core.module.descriptor.DependencyDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolver which looks for definitions first in defined Client Modules, before delegating to the user-defined resolver chain.
 */
public class DependencyToModuleResolverChain implements DependencyToModuleResolver {
    private static final Logger LOGGER = LoggerFactory.getLogger(DependencyToModuleResolverChain.class);
    private final DependencyToModuleResolver clientModuleResolver;
    private final DependencyToModuleResolver projectResolver;
    private final DependencyToModuleResolver ivyDependencyResolver;

    public DependencyToModuleResolverChain(DependencyToModuleResolver clientModuleResolver, DependencyToModuleResolver projectResolver, DependencyToModuleResolver ivyDependencyResolver) {
        this.clientModuleResolver = clientModuleResolver;
        this.projectResolver = projectResolver;
        this.ivyDependencyResolver = ivyDependencyResolver;
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
}
