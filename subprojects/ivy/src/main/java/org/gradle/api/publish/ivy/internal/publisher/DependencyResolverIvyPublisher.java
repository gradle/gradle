/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.publish.ivy.internal.publisher;

import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.repositories.PublicationAwareRepository;
import org.gradle.api.publish.ivy.IvyArtifact;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetadata;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetadata;
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier;
import org.gradle.internal.component.model.DefaultIvyArtifactName;
import org.gradle.internal.component.model.IvyArtifactName;

import java.io.IOException;

public class DependencyResolverIvyPublisher implements IvyPublisher {

    public void publish(IvyNormalizedPublication publication, PublicationAwareRepository repository) {
        ModuleVersionPublisher publisher = repository.createPublisher();
        IvyPublicationIdentity projectIdentity = publication.getProjectIdentity();
        ModuleComponentIdentifier moduleVersionIdentifier = DefaultModuleComponentIdentifier.newId(projectIdentity.getOrganisation(), projectIdentity.getModule(), projectIdentity.getRevision());
        // This indicates the IvyPublishMetaData should probably not be responsible for creating a ModuleDescriptor...
        BuildableIvyModulePublishMetadata publishMetaData = new DefaultIvyModulePublishMetadata(moduleVersionIdentifier, "");

        try {
            for (IvyArtifact publishArtifact : publication.getArtifacts()) {
                publishMetaData.addArtifact(createIvyArtifact(publishArtifact), publishArtifact.getFile());
            }

            IvyArtifactName artifact = new DefaultIvyArtifactName("ivy", "ivy", "xml");
            publishMetaData.addArtifact(artifact, publication.getDescriptorFile());

            publisher.publish(publishMetaData);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private IvyArtifactName createIvyArtifact(IvyArtifact ivyArtifact) {
        return new DefaultIvyArtifactName(ivyArtifact.getName(), ivyArtifact.getType(), ivyArtifact.getExtension(), ivyArtifact.getClassifier());
    }
}
