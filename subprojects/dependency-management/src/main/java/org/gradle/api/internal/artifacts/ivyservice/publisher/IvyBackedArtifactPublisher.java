/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.publisher;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.ConfigurationComponentMetaDataBuilder;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetadata;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.external.model.IvyModuleArtifactPublishMetadata;
import org.gradle.internal.component.external.model.IvyModulePublishMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class IvyBackedArtifactPublisher implements ArtifactPublisher {
    private final ConfigurationComponentMetaDataBuilder configurationComponentMetaDataBuilder;
    private final IvyDependencyPublisher dependencyPublisher;
    private final IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    public IvyBackedArtifactPublisher(ConfigurationComponentMetaDataBuilder configurationComponentMetaDataBuilder,
                                      IvyDependencyPublisher dependencyPublisher,
                                      IvyModuleDescriptorWriter ivyModuleDescriptorWriter) {
        this.configurationComponentMetaDataBuilder = configurationComponentMetaDataBuilder;
        this.dependencyPublisher = dependencyPublisher;
        this.ivyModuleDescriptorWriter = ivyModuleDescriptorWriter;
    }

    public void publish(final Iterable<? extends PublicationAwareRepository> repositories, final Module module, final Configuration configuration, final File descriptor) throws PublishException {
        Set<ConfigurationInternal> allConfigurations = Cast.uncheckedCast(configuration.getAll());
        Set<ConfigurationInternal> configurationsToPublish = Cast.uncheckedCast(configuration.getHierarchy());

        if (descriptor != null) {
            // Convert once, in order to write the Ivy descriptor with _all_ configurations
            IvyModulePublishMetadata publishMetaData = toPublishMetaData(module, allConfigurations);
            validatePublishMetaData(publishMetaData);
            ivyModuleDescriptorWriter.write(publishMetaData, descriptor);
        }

        // Convert a second time with only the published configurations: this ensures that the correct artifacts are included
        BuildableIvyModulePublishMetadata publishMetaData = toPublishMetaData(module, configurationsToPublish);
        if (descriptor != null) {
            IvyArtifactName artifact = new DefaultIvyArtifactName("ivy", "ivy", "xml");
            publishMetaData.addArtifact(artifact, descriptor);
        }

        List<ModuleVersionPublisher> publishResolvers = new ArrayList<ModuleVersionPublisher>();
        for (PublicationAwareRepository repository : repositories) {
            ModuleVersionPublisher publisher = repository.createPublisher();
            publishResolvers.add(publisher);
        }

        dependencyPublisher.publish(publishResolvers, publishMetaData);
    }

    private void validatePublishMetaData(IvyModulePublishMetadata publishMetaData) {
        for (IvyModuleArtifactPublishMetadata metadata : publishMetaData.getArtifacts()) {
            if (metadata.getFile().isDirectory()) {
                throw new IllegalArgumentException("Cannot publish a directory (" + metadata.getFile() + ")");
            }
        }
    }

    private BuildableIvyModulePublishMetadata toPublishMetaData(Module module, Set<? extends ConfigurationInternal> configurations) {
        ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId(module.getGroup(), module.getName(), module.getVersion());
        DefaultIvyModulePublishMetadata publishMetaData = new DefaultIvyModulePublishMetadata(id, module.getStatus());
        configurationComponentMetaDataBuilder.addConfigurations(publishMetaData, configurations);
        return publishMetaData;
    }

}
