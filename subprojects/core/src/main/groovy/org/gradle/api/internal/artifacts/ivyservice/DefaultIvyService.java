/*
 * Copyright 2007-2008 the original author or authors.
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

import org.apache.ivy.Ivy;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.internal.artifacts.IvyService;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.configurations.DependencyMetaDataProvider;
import org.gradle.api.internal.artifacts.configurations.ResolverProvider;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class DefaultIvyService implements IvyService {
    private SettingsConverter settingsConverter;
    private ModuleDescriptorConverter resolveModuleDescriptorConverter;
    private ModuleDescriptorConverter publishModuleDescriptorConverter;
    private ModuleDescriptorConverter fileModuleDescriptorConverter;
    private IvyFactory ivyFactory;
    private IvyDependencyResolver dependencyResolver;
    private IvyDependencyPublisher dependencyPublisher;
    private final DependencyMetaDataProvider metaDataProvider;
    private final ResolverProvider resolverProvider;
    private Map clientModuleRegistry;

    public DefaultIvyService(DependencyMetaDataProvider metaDataProvider, ResolverProvider resolverProvider,
                             SettingsConverter settingsConverter,
                             ModuleDescriptorConverter resolveModuleDescriptorConverter,
                             ModuleDescriptorConverter publishModuleDescriptorConverter,
                             ModuleDescriptorConverter fileModuleDescriptorConverter,
                             IvyFactory ivyFactory,
                             IvyDependencyResolver dependencyResolver,
                             IvyDependencyPublisher dependencyPublisher,
                             Map clientModuleRegistry) {
        this.metaDataProvider = metaDataProvider;
        this.resolverProvider = resolverProvider;
        this.settingsConverter = settingsConverter;
        this.resolveModuleDescriptorConverter = resolveModuleDescriptorConverter;
        this.publishModuleDescriptorConverter = publishModuleDescriptorConverter;
        this.fileModuleDescriptorConverter = fileModuleDescriptorConverter;
        this.ivyFactory = ivyFactory;
        this.dependencyResolver = dependencyResolver;
        this.dependencyPublisher = dependencyPublisher;
        this.clientModuleRegistry = clientModuleRegistry;
    }

    private Ivy ivyForResolve(List<DependencyResolver> dependencyResolvers, File cacheParentDir,
                   Map<String, ModuleDescriptor> clientModuleRegistry) {
        return ivyFactory.createIvy(
                settingsConverter.convertForResolve(
                        dependencyResolvers,
                        cacheParentDir,
                        metaDataProvider.getInternalRepository(),
                        clientModuleRegistry
                )
        );
    }

    private Ivy ivyForPublish(List<DependencyResolver> publishResolvers, File cacheParentDir) {
        return ivyFactory.createIvy(
                settingsConverter.convertForPublish(
                        publishResolvers,
                        cacheParentDir,
                        metaDataProvider.getInternalRepository()
                )
        );
    }

    public SettingsConverter getSettingsConverter() {
        return settingsConverter;
    }

    public ModuleDescriptorConverter getResolveModuleDescriptorConverter() {
        return resolveModuleDescriptorConverter;
    }

    public ModuleDescriptorConverter getPublishModuleDescriptorConverter() {
        return publishModuleDescriptorConverter;
    }

    public ModuleDescriptorConverter getFileModuleDescriptorConverter() {
        return fileModuleDescriptorConverter;
    }

    public IvyFactory getIvyFactory() {
        return ivyFactory;
    }

    public DependencyMetaDataProvider getMetaDataProvider() {
        return metaDataProvider;
    }

    public ResolverProvider getResolverProvider() {
        return resolverProvider;
    }

    public IvyDependencyResolver getDependencyResolver() {
        return dependencyResolver;
    }

    public IvyDependencyPublisher getDependencyPublisher() {
        return dependencyPublisher;
    }

    public ResolvedConfiguration resolve(final Configuration configuration) {
        Ivy ivy = ivyForResolve(resolverProvider.getResolvers(), metaDataProvider.getGradleUserHomeDir(),
                clientModuleRegistry);
        ModuleDescriptor moduleDescriptor = resolveModuleDescriptorConverter.convert(configuration.getAll(),
                metaDataProvider.getModule(), ivy.getSettings());
        return dependencyResolver.resolve(configuration, ivy, moduleDescriptor);
    }

    public void publish(Set<Configuration> configurationsToPublish, File descriptorDestination,
                        List<DependencyResolver> publishResolvers) {
        Ivy ivy = ivyForPublish(publishResolvers, metaDataProvider.getGradleUserHomeDir());
        Set<String> confs = Configurations.getNames(configurationsToPublish, false);
        writeDescriptorFile(descriptorDestination, configurationsToPublish, ivy.getSettings());
        dependencyPublisher.publish(
                confs,
                publishResolvers,
                publishModuleDescriptorConverter.convert(configurationsToPublish, metaDataProvider.getModule(), ivy.getSettings()),
                descriptorDestination,
                ivy.getPublishEngine());
    }

    private void writeDescriptorFile(File descriptorDestination, Set<Configuration> configurationsToPublish, IvySettings ivySettings) {
        if (descriptorDestination == null) {
            return;
        }
        assert configurationsToPublish.size() > 0;
        Set<Configuration> allConfigurations = configurationsToPublish.iterator().next().getAll();
        ModuleDescriptor moduleDescriptor = fileModuleDescriptorConverter.convert(allConfigurations, metaDataProvider.getModule(), ivySettings);
        try {
            moduleDescriptor.toIvyFile(descriptorDestination);
        } catch (ParseException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void setSettingsConverter(SettingsConverter settingsConverter) {
        this.settingsConverter = settingsConverter;
    }

    public void setIvyFactory(IvyFactory ivyFactory) {
        this.ivyFactory = ivyFactory;
    }

    public void setDependencyResolver(IvyDependencyResolver dependencyResolver) {
        this.dependencyResolver = dependencyResolver;
    }

    public void setDependencyPublisher(IvyDependencyPublisher dependencyPublisher) {
        this.dependencyPublisher = dependencyPublisher;
    }

    public Map getClientModuleRegistry() {
        return clientModuleRegistry;
    }

    public void setClientModuleRegistry(Map clientModuleRegistry) {
        this.clientModuleRegistry = clientModuleRegistry;
    }
}
