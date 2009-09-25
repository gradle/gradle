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
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.*;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependenciesToModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultDependencyDescriptorFactory;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.DefaultClientModuleDescriptorFactory;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultConfigurationContainerFactory implements ConfigurationContainerFactory {
    private Map clientModuleRegistry;

    public DefaultConfigurationContainerFactory(Map clientModuleRegistry) {
        this.clientModuleRegistry = clientModuleRegistry;
    }

    public ConfigurationHandler createConfigurationContainer(ResolverProvider resolverProvider,
                                                             DependencyMetaDataProvider dependencyMetaDataProvider) {
        DefaultExcludeRuleConverter excludeRuleConverter = new DefaultExcludeRuleConverter();
        IvyService ivyService = new ErrorHandlingIvyService(
                new ShortcircuitEmptyConfigsIvyService(
                        new DefaultIvyService(
                                dependencyMetaDataProvider,
                                resolverProvider,
                                new DefaultSettingsConverter(),
                                new DefaultModuleDescriptorConverter(
                                        new DefaultModuleDescriptorFactory(),
                                        new DefaultConfigurationsToModuleDescriptorConverter(),
                                        new DefaultDependenciesToModuleDescriptorConverter(
                                                new DefaultDependencyDescriptorFactory(excludeRuleConverter,
                                                        new DefaultClientModuleDescriptorFactory(), clientModuleRegistry),
                                                excludeRuleConverter),
                                        new DefaultArtifactsToModuleDescriptorConverter()),
                                new DefaultIvyFactory(),
                                new SelfResolvingDependencyResolver(
                                        new DefaultIvyDependencyResolver(new DefaultIvyReportConverter())),
                                new DefaultIvyDependencyPublisher(new DefaultModuleDescriptorForUploadConverter(), new DefaultPublishOptionsFactory()),
                                clientModuleRegistry)));
        return new DefaultConfigurationHandler(ivyService);
    }
}
