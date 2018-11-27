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

package org.gradle.api.publish.maven.internal.publisher;

import com.google.common.base.Strings;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.publication.maven.internal.action.MavenPublishAction;
import org.gradle.api.publish.maven.MavenArtifact;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractMavenPublisher implements MavenPublisher {

    private final static Logger LOGGER = LoggerFactory.getLogger(AbstractMavenPublisher.class);
    private final LocalMavenRepositoryLocator mavenRepositoryLocator;

    public AbstractMavenPublisher(LocalMavenRepositoryLocator mavenRepositoryLocator) {
        this.mavenRepositoryLocator = mavenRepositoryLocator;
    }

    public void publish(MavenNormalizedPublication publication, MavenArtifactRepository artifactRepository) {
        if (artifactRepository == null) {
            LOGGER.info("Publishing to maven local repository");
        } else {
            LOGGER.info("Publishing to repository '{}' ({})", artifactRepository.getName(), artifactRepository.getUrl());
        }

        MavenPublishAction deployTask = createDeployTask(publication.getPackaging(), publication.getProjectIdentity(), mavenRepositoryLocator, artifactRepository);
        addPomAndArtifacts(deployTask, publication);
        execute(deployTask);
    }

    abstract protected MavenPublishAction createDeployTask(String packaging, MavenProjectIdentity projectIdentity, LocalMavenRepositoryLocator mavenRepositoryLocator, MavenArtifactRepository artifactRepository);

    private void addPomAndArtifacts(MavenPublishAction publishAction, MavenNormalizedPublication publication) {
        MavenArtifact pomArtifact = publication.getPomArtifact();
        publishAction.setPomArtifact(pomArtifact.getFile());

        MavenArtifact mainArtifact = publication.getMainArtifact();
        if (mainArtifact != null) {
            publishAction.setMainArtifact(mainArtifact.getFile());
        }

        for (MavenArtifact artifact : publication.getAllArtifacts()) {
            if (artifact == mainArtifact || artifact == pomArtifact) {
                continue;
            }
            publishAction.addAdditionalArtifact(artifact.getFile(), Strings.nullToEmpty(artifact.getExtension()), Strings.nullToEmpty(artifact.getClassifier()));
        }
    }

    private void execute(MavenPublishAction publishAction) {
        publishAction.publish();
    }
}
