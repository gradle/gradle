/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.maven.tasks;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.TaskAction;

/**
 * Publishes a {@link org.gradle.api.publish.maven.MavenPublication} to a {@link MavenArtifactRepository}.
 *
 * @since 1.4
 */
public class PublishToMavenRepository extends AbstractPublishToMaven {
    private MavenArtifactRepository repository;

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     */
    @Internal
    public MavenArtifactRepository getRepository() {
        return repository;
    }

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     */
    public void setRepository(MavenArtifactRepository repository) {
        this.repository = repository;
    }

    @TaskAction
    public void publish() {
        MavenPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }

        MavenArtifactRepository repository = getRepository();
        if (repository == null) {
            throw new InvalidUserDataException("The 'repository' property is required");
        }

        getDuplicatePublicationTracker().checkCanPublish(publicationInternal, repository.getUrl(), repository.getName());
        doPublish(publicationInternal, repository);
    }

    private void doPublish(final MavenPublicationInternal publication, final MavenArtifactRepository repository) {
        new PublishOperation(publication, repository.getName()) {
            @Override
            protected void publish() {
                MavenPublisher remotePublisher = getMavenPublishers().getRemotePublisher(getTemporaryDirFactory());
                remotePublisher = new ValidatingMavenPublisher(remotePublisher);
                remotePublisher.publish(publication.asNormalisedPublication(), repository);
            }
        }.run();
    }
}
