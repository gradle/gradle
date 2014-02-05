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

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.ArtifactRepositoryContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.AntTaskBackedMavenLocalPublisher;
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.StaticLockingMavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;

import javax.inject.Inject;

/**
 * Publishes a {@link org.gradle.api.publish.maven.MavenPublication} to the Maven Local repository.
 *
 * @since 1.4
 */
@Incubating
public class PublishToMavenLocal extends PublishToMavenRepository {

    private final BaseRepositoryFactory baseRepositoryFactory;

    @Inject
    public PublishToMavenLocal(Factory<LoggingManagerInternal> loggingManagerFactory, BaseRepositoryFactory baseRepositoryFactory) {
        super(loggingManagerFactory);
        this.baseRepositoryFactory = baseRepositoryFactory;
    }

    @Override
    public MavenArtifactRepository getRepository() {
        if (super.getRepository() == null) {
            // Instantiate the default MavenLocal repository if none has been set explicitly
            MavenArtifactRepository mavenLocalRepository = baseRepositoryFactory.createMavenLocalRepository();
            mavenLocalRepository.setName(ArtifactRepositoryContainer.DEFAULT_MAVEN_LOCAL_REPO_NAME);
            setRepository(mavenLocalRepository);
        }

        return super.getRepository();
    }

    @Override
    protected void doPublish(final MavenPublicationInternal publication, final MavenArtifactRepository repository) {
        new PublishOperation(publication, repository) {
            @Override
            protected void publish() throws Exception {
                MavenPublisher antBackedPublisher = new AntTaskBackedMavenLocalPublisher(getLoggingManagerFactory(), getTemporaryDirFactory());
                MavenPublisher staticLockingPublisher = new StaticLockingMavenPublisher(antBackedPublisher);
                MavenPublisher validatingPublisher = new ValidatingMavenPublisher(staticLockingPublisher);
                validatingPublisher.publish(publication.asNormalisedPublication(), repository);
            }
        }.run();
    }
}
