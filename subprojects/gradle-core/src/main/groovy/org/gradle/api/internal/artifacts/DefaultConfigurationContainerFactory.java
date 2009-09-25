/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.api.artifacts.dsl.ConfigurationHandler;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;
import org.gradle.api.internal.artifacts.dsl.DefaultConfigurationHandler;
import org.gradle.api.internal.artifacts.ivyservice.*;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationContainerFactory implements ConfigurationContainerFactory {
    private Map clientModuleRegistry;
    private SettingsConverter settingsConverter;
    private ModuleDescriptorConverter moduleDescriptorConverter;
    private IvyFactory ivyFactory;
    private IvyDependencyResolver dependencyResolver;
    private IvyDependencyPublisher dependencyPublisher;

    public DefaultConfigurationContainerFactory(Map clientModuleRegistry, SettingsConverter settingsConverter,
                                                ModuleDescriptorConverter moduleDescriptorConverter, IvyFactory ivyFactory,
                                                IvyDependencyResolver dependencyResolver, IvyDependencyPublisher dependencyPublisher) {
        this.clientModuleRegistry = clientModuleRegistry;
        this.settingsConverter = settingsConverter;
        this.dependencyPublisher = dependencyPublisher;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.ivyFactory = ivyFactory;
        this.dependencyResolver = dependencyResolver;
    }

    public ConfigurationHandler createConfigurationContainer(ResolverProvider resolverProvider,
                                                             DependencyMetaDataProvider dependencyMetaDataProvider) {
        IvyService ivyService = new ErrorHandlingIvyService(
                new ShortcircuitEmptyConfigsIvyService(
                        new DefaultIvyService(
                                dependencyMetaDataProvider,
                                resolverProvider,
                                settingsConverter,
                                moduleDescriptorConverter,
                                ivyFactory,
                                dependencyResolver,
                                dependencyPublisher,
                                clientModuleRegistry)));
        return new DefaultConfigurationHandler(ivyService);
    }
}
