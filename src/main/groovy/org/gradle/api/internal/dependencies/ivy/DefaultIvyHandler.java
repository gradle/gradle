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
package org.gradle.api.internal.dependencies.ivy;

import org.gradle.api.internal.dependencies.ivy.SettingsConverter;
import org.gradle.api.internal.dependencies.ivy.ModuleDescriptorConverter;
import org.gradle.api.internal.dependencies.*;
import org.gradle.api.dependencies.*;
import org.gradle.api.filter.Filters;
import org.gradle.util.WrapUtil;
import org.gradle.util.GUtil;
import org.apache.ivy.Ivy;
import org.apache.ivy.core.report.ResolveReport;
import org.apache.ivy.core.module.descriptor.ModuleDescriptor;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultIvyHandler implements IvyHandler {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyHandler.class);

    private SettingsConverter settingsConverter;
    private ModuleDescriptorConverter moduleDescriptorConverter;
    private IvyFactory ivyFactory;
    private BuildResolverHandler buildResolverHandler;
    private IvyDependencyResolver dependencyResolver;
    private IvyDependencyPublisher dependencyPublisher;

    public DefaultIvyHandler(SettingsConverter settingsConverter, ModuleDescriptorConverter moduleDescriptorConverter,
                             IvyFactory ivyFactory, BuildResolverHandler buildResolverHandler, IvyDependencyResolver dependencyResolver,
                             IvyDependencyPublisher dependencyPublisher) {
        this.settingsConverter = settingsConverter;
        this.moduleDescriptorConverter = moduleDescriptorConverter;
        this.ivyFactory = ivyFactory;
        this.buildResolverHandler = buildResolverHandler;
        this.dependencyResolver = dependencyResolver;
        this.dependencyPublisher = dependencyPublisher;
    }

    public Ivy ivy(List<DependencyResolver> dependencyResolvers, List<DependencyResolver> publishResolvers, File gradleUserHome,
                   Map<String, ModuleDescriptor> clientModuleRegistry) {
        return ivyFactory.createIvy(
                settingsConverter.convert(
                        dependencyResolvers,
                        publishResolvers,
                        gradleUserHome,
                        buildResolverHandler.getBuildResolver(),
                        clientModuleRegistry
                )
        );
    }

    public SettingsConverter getSettingsConverter() {
        return settingsConverter;
    }

    public ModuleDescriptorConverter getModuleDescriptorConverter() {
        return moduleDescriptorConverter;
    }

    public IvyFactory getIvyFactory() {
        return ivyFactory;
    }

    public BuildResolverHandler getBuildResolverHandler() {
        return buildResolverHandler;
    }

    public IvyDependencyResolver getDependencyResolver() {
        return dependencyResolver;
    }

    public IvyDependencyPublisher getDependencyPublisher() {
        return dependencyPublisher;
    }

    public ResolveReport getLastResolveReport() {
        return dependencyResolver.getLastResolveReport();
    }

    public List<File> resolve(String conf, Set<? extends Configuration> configurations, DependencyContainerInternal dependencyContainer,
                                         List<DependencyResolver> dependencyResolvers, ResolveInstruction resolveInstruction, File gradleUserHome) {
        ResolveReport resolveReport = resolveAsReport(conf, configurations, dependencyContainer, dependencyResolvers, resolveInstruction, gradleUserHome);
        return dependencyResolver.resolveFromReport(conf, resolveReport);
    }

    public List<File> resolveFromReport(String conf, ResolveReport resolveReport) {
        return dependencyResolver.resolveFromReport(conf, resolveReport);
    }

    public ResolveReport resolveAsReport(String conf, Set<? extends Configuration> configurations, DependencyContainerInternal dependencyContainer,
                                         List<DependencyResolver> dependencyResolvers,
                                         ResolveInstruction resolveInstruction, File gradleUserHome) {
        Ivy ivy = ivy(dependencyResolvers,
                    new ArrayList<DependencyResolver>(),
                    gradleUserHome,
                    dependencyContainer.getClientModuleRegistry());
        ModuleDescriptor moduleDescriptor = moduleDescriptorConverter.convert(WrapUtil.toMap(conf, resolveInstruction.isTransitive()), new DefaultConfigurationContainer(configurations), Filters.<Configuration>noFilter(),
                dependencyContainer, resolveInstruction.getDependencyFilter(), ArtifactContainer.EMPTY_CONTAINER, Filters.<PublishArtifact>noFilter());
        return dependencyResolver.resolveAsReport(conf, resolveInstruction, ivy, moduleDescriptor);
    }



    public void publish(String configuration, PublishInstruction publishInstruction,
                        List<DependencyResolver> publishResolvers, ConfigurationContainer configurationContainer,
                        DependencyContainerInternal dependencyContainer,
                        ArtifactContainer artifactContainer, File gradleUserHome) {
        Ivy ivy = ivy(new ArrayList<DependencyResolver>(),
                publishResolvers,
                gradleUserHome,
                new HashMap());
        Set<String> confs = Configurations.getNames(configurationContainer.get(configuration).getChain());
        dependencyPublisher.publish(
                confs,
                publishInstruction,
                publishResolvers,
                moduleDescriptorConverter.convert(new HashMap<String, Boolean>(), configurationContainer, publishInstruction.getModuleDescriptor().getConfigurationFilter(),
                        dependencyContainer, publishInstruction.getModuleDescriptor().getDependencyFilter(),
                        artifactContainer, publishInstruction.getArtifactFilter()),
                ivy.getPublishEngine());
    }
}
