/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.internal.component.external.ivypublish;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ArtifactPublisher;
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier;
import org.gradle.api.internal.artifacts.Module;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.configurations.ConfigurationInternal;
import org.gradle.api.internal.artifacts.configurations.Configurations;
import org.gradle.api.internal.artifacts.configurations.OutgoingVariant;
import org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies.LocalConfigurationMetadataBuilder;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.internal.Cast;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.local.model.BuildableLocalConfigurationMetadata;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class DefaultArtifactPublisher implements ArtifactPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultArtifactPublisher.class);

    private final LocalConfigurationMetadataBuilder dependenciesConverter;
    private final IvyModuleDescriptorWriter ivyModuleDescriptorWriter;

    public DefaultArtifactPublisher(LocalConfigurationMetadataBuilder dependenciesConverter,
                                    IvyModuleDescriptorWriter ivyModuleDescriptorWriter) {
        this.dependenciesConverter = dependenciesConverter;
        this.ivyModuleDescriptorWriter = ivyModuleDescriptorWriter;
    }

    @Override
    public void publish(final Iterable<? extends PublicationAwareRepository> repositories, final Module module, final Configuration configuration, final File descriptor) throws PublishException {
        Set<ConfigurationInternal> allConfigurations = Cast.uncheckedCast(configuration.getAll());
        Set<ConfigurationInternal> configurationsToPublish = Cast.uncheckedCast(configuration.getHierarchy());

        // Will create `ivy.xml` even for Maven publishing! (as long as `Upload.uploadDescriptor == true`)
        if (descriptor != null) {
            // Convert once, in order to write the Ivy descriptor with _all_ configurations
            DefaultIvyModulePublishMetadata publishMetaData = toPublishMetaData(module, allConfigurations, false);
            ivyModuleDescriptorWriter.write(publishMetaData, descriptor);
        }

        // Convert a second time with only the published configurations: this ensures that the correct artifacts are included
        DefaultIvyModulePublishMetadata publishMetaData = toPublishMetaData(module, configurationsToPublish, true);
        if (descriptor != null) {
            IvyArtifactName artifact = new DefaultIvyArtifactName("ivy", "ivy", "xml");
            publishMetaData.addArtifact(artifact, descriptor);
        }

        for (PublicationAwareRepository repository : repositories) {
            ModuleVersionPublisher publisher = repository.createPublisher();
            LOGGER.info("Publishing to {}", publisher);
            publisher.publish(publishMetaData);
        }
    }

    private DefaultIvyModulePublishMetadata toPublishMetaData(Module module, Set<? extends ConfigurationInternal> configurations, boolean validateArtifacts) {
        ModuleComponentIdentifier id = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(module.getGroup(), module.getName()), module.getVersion());
        DefaultIvyModulePublishMetadata publishMetaData = new DefaultIvyModulePublishMetadata(id, module.getStatus());
        addConfigurations(publishMetaData, configurations, validateArtifacts);
        return publishMetaData;
    }

    private void addConfigurations(DefaultIvyModulePublishMetadata metaData, Collection<? extends ConfigurationInternal> configurations, boolean validateArtifacts) {
        for (ConfigurationInternal configuration : configurations) {
            BuildableLocalConfigurationMetadata configurationMetadata = addConfiguration(metaData, configuration);
            dependenciesConverter.addDependenciesAndExcludes(configurationMetadata, configuration);

            OutgoingVariant outgoingVariant = configuration.convertToOutgoingVariant();
            for (PublishArtifact publishArtifact : outgoingVariant.getArtifacts()) {
                if (!validateArtifacts || isValidToPublish(publishArtifact)) {
                    metaData.addArtifact(configuration.getName(), publishArtifact);
                }
            }
        }
    }

    private BuildableLocalConfigurationMetadata addConfiguration(DefaultIvyModulePublishMetadata metaData, ConfigurationInternal configuration) {
        configuration.preventFromFurtherMutation();
        return metaData.addConfiguration(configuration.getName(), Configurations.getNames(configuration.getExtendsFrom()), configuration.isVisible(), configuration.isTransitive());
    }

    private boolean isValidToPublish(PublishArtifact artifact) {
        File artifactFile = artifact.getFile();
        if (artifactFile.isDirectory()) {
            throw new IllegalArgumentException("Cannot publish a directory (" + artifactFile + ")");
        }

        if (artifactFile.exists()) {
            return true;
        }
        IvyArtifactName ivyArtifactName = DefaultIvyArtifactName.forPublishArtifact(artifact);
        if (!isSigningArtifact(ivyArtifactName)) {
            throw new PublishException(String.format("Cannot publish artifact '%s' (%s) as it does not exist.", ivyArtifactName, artifactFile));
        }
        return false;
    }

    private boolean isSigningArtifact(IvyArtifactName artifact) {
        return artifact.getType().endsWith(".asc") || artifact.getType().endsWith(".sig");
    }


}
