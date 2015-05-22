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
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.MDArtifact;
import org.gradle.api.Action;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.ModuleInternal;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ComponentConverterSource;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.IvyModulePublishMetaData;
import org.gradle.internal.component.local.model.LocalComponentMetaData;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IvyBackedArtifactPublisher implements ArtifactPublisher {
    private final LocalComponentFactory publishLocalComponentFactory;
    private final IvyContextManager ivyContextManager;
    private final IvyDependencyPublisher dependencyPublisher;
    private final IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    public IvyBackedArtifactPublisher(LocalComponentFactory publishLocalComponentFactory,
                                      IvyContextManager ivyContextManager,
                                      IvyDependencyPublisher dependencyPublisher,
                                      IvyModuleDescriptorWriter ivyModuleDescriptorWriter) {
        this.publishLocalComponentFactory = publishLocalComponentFactory;
        this.ivyContextManager = ivyContextManager;
        this.dependencyPublisher = dependencyPublisher;
        this.ivyModuleDescriptorWriter = ivyModuleDescriptorWriter;
    }

    public void publish(final Iterable<? extends PublicationAwareRepository> repositories, final ModuleInternal module, final Configuration configuration, final File descriptor) throws PublishException {
        ivyContextManager.withIvy(new Action<Ivy>() {
            public void execute(Ivy ivy) {
                Set<Configuration> allConfigurations = configuration.getAll();
                Set<Configuration> configurationsToPublish = configuration.getHierarchy();

                LocalComponentMetaData allConfigurationsComponentMetaData = publishLocalComponentFactory.convert(new ComponentConverterSource(allConfigurations, module));
                if (descriptor != null) {
                    IvyModulePublishMetaData publishMetaData = allConfigurationsComponentMetaData.toPublishMetaData();
                    ivyModuleDescriptorWriter.write(publishMetaData.getModuleDescriptor(), publishMetaData.getArtifacts(), descriptor);
                }

                // Need to convert a second time, to determine which artifacts to publish (and yes, this isn't a great way to do things...)
                LocalComponentMetaData componentMetaData = publishLocalComponentFactory.convert(new ComponentConverterSource(configurationsToPublish, module));
                BuildableIvyModulePublishMetaData publishMetaData = componentMetaData.toPublishMetaData();
                if (descriptor != null) {
                    Artifact artifact = MDArtifact.newIvyArtifact(publishMetaData.getModuleDescriptor());
                    publishMetaData.addArtifact(artifact, descriptor);
                }

                List<ModuleVersionPublisher> publishResolvers = new ArrayList<ModuleVersionPublisher>();
                for (PublicationAwareRepository repository : repositories) {
                    ModuleVersionPublisher publisher = repository.createPublisher();
                    publishResolvers.add(publisher);
                }

                dependencyPublisher.publish(publishResolvers, publishMetaData);
            }
        });
    }
}
