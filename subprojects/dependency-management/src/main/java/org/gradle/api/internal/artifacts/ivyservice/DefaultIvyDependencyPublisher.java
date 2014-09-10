/*
 * Copyright 2009 the original author or authors.
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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.internal.component.external.model.BuildableIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.DefaultIvyModulePublishMetaData;
import org.gradle.internal.component.external.model.IvyModuleArtifactPublishMetaData;
import org.gradle.internal.component.external.model.IvyModulePublishMetaData;
import org.gradle.internal.component.model.IvyArtifactName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class DefaultIvyDependencyPublisher implements IvyDependencyPublisher {
    private static Logger logger = LoggerFactory.getLogger(DefaultIvyDependencyPublisher.class);

    public void publish(List<ModuleVersionPublisher> publishResolvers,
                        IvyModulePublishMetaData publishMetaData) {
        try {
            // Make a copy of the publication and filter missing artifacts
            DefaultIvyModulePublishMetaData publication = new DefaultIvyModulePublishMetaData(publishMetaData.getId());
            for (IvyModuleArtifactPublishMetaData artifact: publishMetaData.getArtifacts()) {
                addPublishedArtifact(artifact, publication);
            }
            for (ModuleVersionPublisher publisher : publishResolvers) {
                logger.info("Publishing to {}", publisher);
                publisher.publish(publication);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void addPublishedArtifact(IvyModuleArtifactPublishMetaData artifact, BuildableIvyModulePublishMetaData publication) {
        if (checkArtifactFileExists(artifact)) {
            publication.addArtifact(artifact);
        }
    }

    private boolean checkArtifactFileExists(IvyModuleArtifactPublishMetaData artifact) {
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
