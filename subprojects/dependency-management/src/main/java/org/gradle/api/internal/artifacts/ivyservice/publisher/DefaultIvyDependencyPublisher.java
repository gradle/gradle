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

import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetadata;
import org.gradle.internal.component.external.model.IvyModuleArtifactPublishMetadata;
import org.gradle.internal.component.external.model.IvyModulePublishMetadata;
import org.gradle.internal.component.model.IvyArtifactName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class DefaultIvyDependencyPublisher implements IvyDependencyPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultIvyDependencyPublisher.class);

    public void publish(List<ModuleVersionPublisher> publishResolvers,
                        IvyModulePublishMetadata publishMetaData) {
        // Make a copy of the publication and filter missing artifacts
        DefaultIvyModulePublishMetadata publication = new DefaultIvyModulePublishMetadata(publishMetaData);
        for (IvyModuleArtifactPublishMetadata artifact : publishMetaData.getArtifacts()) {
            addPublishedArtifact(artifact, publication);
        }
        for (ModuleVersionPublisher publisher : publishResolvers) {
            LOGGER.info("Publishing to {}", publisher);
            publisher.publish(publication);
        }
    }

    private void addPublishedArtifact(IvyModuleArtifactPublishMetadata artifact, DefaultIvyModulePublishMetadata publication) {
        if (checkArtifactFileExists(artifact)) {
            publication.addArtifact(artifact);
        }
    }

    private boolean checkArtifactFileExists(IvyModuleArtifactPublishMetadata artifact) {
        File artifactFile = artifact.getFile();
        if (artifactFile.exists()) {
            return true;
        }
        if (!isSigningArtifact(artifact.getArtifactName())) {
            throw new PublishException(String.format("Cannot publish artifact '%s' (%s) as it does not exist.", artifact.getId(), artifactFile));
        }
        return false;
    }

    private boolean isSigningArtifact(IvyArtifactName artifact) {
        return artifact.getType().endsWith(".asc") || artifact.getType().endsWith(".sig");
    }
}
