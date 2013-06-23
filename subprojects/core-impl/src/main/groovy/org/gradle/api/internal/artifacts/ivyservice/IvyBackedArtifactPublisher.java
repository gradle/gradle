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
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Module;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.ModuleVersionPublishMetaData;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;

import java.io.File;
import java.util.ArrayList;
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
    private final IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    public IvyBackedArtifactPublisher(SettingsConverter settingsConverter,
                                      ModuleDescriptorConverter publishModuleDescriptorConverter,
                                      IvyFactory ivyFactory,
                                      IvyDependencyPublisher dependencyPublisher,
                                      IvyModuleDescriptorWriter ivyModuleDescriptorWriter) {
        this.settingsConverter = settingsConverter;
        this.publishModuleDescriptorConverter = publishModuleDescriptorConverter;
        this.ivyFactory = ivyFactory;
        this.dependencyPublisher = dependencyPublisher;
        this.ivyModuleDescriptorWriter = ivyModuleDescriptorWriter;
    }

    public void publish(Iterable<? extends PublicationAwareRepository> repositories, Module module, Set<? extends Configuration> configurations, File descriptor) throws PublishException {
        Set<Configuration> allConfigurations = configurations.iterator().next().getAll();
        ModuleVersionPublishMetaData publishMetaData = publishModuleDescriptorConverter.convert(allConfigurations, module);
        if (descriptor != null) {
            ModuleDescriptor moduleDescriptor = publishMetaData.getModuleDescriptor();
            ivyModuleDescriptorWriter.write(moduleDescriptor, descriptor);
        }

        IvySettings settings = settingsConverter.convertForPublish();
        Ivy ivy = ivyFactory.createIvy(settings);
        List<ModuleVersionPublisher> publishResolvers = new ArrayList<ModuleVersionPublisher>();
        for (PublicationAwareRepository repository : repositories) {
            ModuleVersionPublisher publisher = repository.createPublisher();
            publisher.setSettings(ivy.getSettings());
            publishResolvers.add(publisher);
        }

        publishMetaData = publishModuleDescriptorConverter.convert(configurations, module);
        Set<String> confs = Configurations.getNames(configurations, false);
        dependencyPublisher.publish(
                confs,
                publishResolvers,
                publishMetaData,
                descriptor);
    }

}
