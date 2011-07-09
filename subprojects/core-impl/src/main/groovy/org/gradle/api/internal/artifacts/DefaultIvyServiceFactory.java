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
package org.gradle.api.internal.artifacts;

import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.ivyservice.*;
import org.gradle.api.internal.artifacts.repositories.InternalRepository;

import java.util.Map;

public class DefaultIvyServiceFactory implements IvyServiceFactory {
    private final Map<String, ModuleDescriptor> clientModuleRegistry;
    private final SettingsConverter settingsConverter;
    private final ModuleDescriptorConverter resolveModuleDescriptorConverter;
    private final ModuleDescriptorConverter publishModuleDescriptorConverter;
    private final ModuleDescriptorConverter fileModuleDescriptorConverter;
    private final IvyFactory ivyFactory;
    private final IvyDependencyResolver dependencyResolver;
    private final IvyDependencyPublisher dependencyPublisher;

    public DefaultIvyServiceFactory(Map<String, ModuleDescriptor> clientModuleRegistry, SettingsConverter settingsConverter,
                                    ModuleDescriptorConverter resolveModuleDescriptorConverter,
                                    ModuleDescriptorConverter publishModuleDescriptorConverter,
                                    ModuleDescriptorConverter fileModuleDescriptorConverter,
                                    IvyFactory ivyFactory,
                                    IvyDependencyResolver dependencyResolver, IvyDependencyPublisher dependencyPublisher) {
        this.clientModuleRegistry = clientModuleRegistry;
        this.settingsConverter = settingsConverter;
        this.resolveModuleDescriptorConverter = resolveModuleDescriptorConverter;
        this.publishModuleDescriptorConverter = publishModuleDescriptorConverter;
        this.fileModuleDescriptorConverter = fileModuleDescriptorConverter;
        this.ivyFactory = ivyFactory;
        this.dependencyResolver = dependencyResolver;
        this.dependencyPublisher = dependencyPublisher;
    }

    public IvyService newIvyService(ResolverProvider resolverProvider, DependencyMetaDataProvider dependencyMetaDataProvider, InternalRepository internalRepository) {
        return new ErrorHandlingIvyService(
                new ShortcircuitEmptyConfigsIvyService(
                        new DefaultIvyService(
                                dependencyMetaDataProvider,
                                resolverProvider,
                                settingsConverter,
                                resolveModuleDescriptorConverter,
                                publishModuleDescriptorConverter,
                                fileModuleDescriptorConverter,
                                ivyFactory,
                                dependencyResolver,
                                dependencyPublisher,
                                internalRepository,
                                clientModuleRegistry)));
    }
}
