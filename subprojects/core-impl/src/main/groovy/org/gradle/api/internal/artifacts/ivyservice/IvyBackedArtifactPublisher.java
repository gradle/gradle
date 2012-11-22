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
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author Hans Dockter
 */
public class IvyBackedArtifactPublisher implements ArtifactPublisher {
    private final SettingsConverter settingsConverter;
    private final ModuleDescriptorConverter publishModuleDescriptorConverter;
    private final IvyFactory ivyFactory;
    private final IvyDependencyPublisher dependencyPublisher;
    private final Iterable<DependencyResolver> dependencyResolvers;

    public IvyBackedArtifactPublisher(Iterable<DependencyResolver> dependencyResolvers,
                                      SettingsConverter settingsConverter,
                                      ModuleDescriptorConverter publishModuleDescriptorConverter,
                                      IvyFactory ivyFactory,
                                      IvyDependencyPublisher dependencyPublisher) {
        this.dependencyResolvers = dependencyResolvers;
        this.settingsConverter = settingsConverter;
        this.publishModuleDescriptorConverter = publishModuleDescriptorConverter;
        this.ivyFactory = ivyFactory;
        this.dependencyPublisher = dependencyPublisher;
    }

    private Ivy ivyForPublish(List<DependencyResolver> publishResolvers) {
        return ivyFactory.createIvy(settingsConverter.convertForPublish(publishResolvers));
    }

    public void publish(Module module, Set<? extends Configuration> configurations, File descriptor) throws PublishException {
        List<DependencyResolver> publishResolvers = CollectionUtils.toList(dependencyResolvers);
        Ivy ivy = ivyForPublish(publishResolvers);
        Set<String> confs = Configurations.getNames(configurations, false);
        dependencyPublisher.publish(
                confs,
                publishResolvers,
                publishModuleDescriptorConverter.convert(configurations, module),
                descriptor,
                ivy.getEventManager());
    }

}
