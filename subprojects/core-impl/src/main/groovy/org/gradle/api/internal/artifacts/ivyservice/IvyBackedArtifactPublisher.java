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
import org.gradle.api.Action;
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
    private final ModuleDescriptorConverter publishModuleDescriptorConverter;
    private final IvyContextManager ivyContextManager;
    private final IvyDependencyPublisher dependencyPublisher;
    private final IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    public IvyBackedArtifactPublisher(ModuleDescriptorConverter publishModuleDescriptorConverter,
                                      IvyContextManager ivyContextManager,
                                      IvyDependencyPublisher dependencyPublisher,
                                      IvyModuleDescriptorWriter ivyModuleDescriptorWriter) {
        this.publishModuleDescriptorConverter = publishModuleDescriptorConverter;
        this.ivyContextManager = ivyContextManager;
        this.dependencyPublisher = dependencyPublisher;
        this.ivyModuleDescriptorWriter = ivyModuleDescriptorWriter;
    }

    public void publish(final Iterable<? extends PublicationAwareRepository> repositories, final Module module, final Set<? extends Configuration> configurations, final File descriptor) throws PublishException {
        ivyContextManager.withIvy(new Action<Ivy>() {
            public void execute(Ivy ivy) {
                Set<Configuration> allConfigurations = configurations.iterator().next().getAll();
                ModuleVersionPublishMetaData publishMetaData = publishModuleDescriptorConverter.convert(allConfigurations, module);
                if (descriptor != null) {
                    ModuleDescriptor moduleDescriptor = publishMetaData.getModuleDescriptor();
                    ivyModuleDescriptorWriter.write(moduleDescriptor, descriptor);
                }

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
        });
    }
}
