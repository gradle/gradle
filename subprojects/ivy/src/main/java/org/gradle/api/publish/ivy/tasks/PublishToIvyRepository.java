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

package org.gradle.api.publish.ivy.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.file.FileCollection;
import org.gradle.api.provider.Property;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.internal.validation.DuplicatePublicationTracker;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublisher;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.artifacts.repositories.AuthenticationSupportedInternal;

import javax.inject.Inject;
import java.util.concurrent.Callable;

/**
 * Publishes an IvyPublication to an IvyArtifactRepository.
 *
 * @since 1.3
 */
public class PublishToIvyRepository extends DefaultTask {

    private IvyPublicationInternal publication;
    private IvyArtifactRepository repository;
    private final Property<Credentials> credentials = getProject().getObjects().property(Credentials.class);

    public PublishToIvyRepository() {

        // Allow the publication to participate in incremental build
        getInputs().files((Callable<FileCollection>) () -> {
            IvyPublicationInternal publicationInternal = getPublicationInternal();
            return publicationInternal == null ? null : publicationInternal.getPublishableArtifacts().getFiles();
        })
            .withPropertyName("publication.publishableFiles")
            .withPathSensitivity(PathSensitivity.NAME_ONLY);

        // Should repositories be able to participate in incremental?
        // At the least, they may be able to express themselves as output files
        // They *might* have input files and other dependencies as well though
        // Inputs: The credentials they need may be expressed in a file
        // Dependencies: Can't think of a case here
    }

    /**
     * The publication to be published.
     *
     * @return The publication to be published
     */
    @Internal
    public IvyPublication getPublication() {
        return publication;
    }

    /**
     * Sets the publication to be published.
     *
     * @param publication The publication to be published
     */
    public void setPublication(IvyPublication publication) {
        this.publication = toPublicationInternal(publication);
    }

    private IvyPublicationInternal getPublicationInternal() {
        return toPublicationInternal(getPublication());
    }

    private static IvyPublicationInternal toPublicationInternal(IvyPublication publication) {
        if (publication == null) {
            return null;
        } else if (publication instanceof IvyPublicationInternal) {
            return (IvyPublicationInternal) publication;
        } else {
            throw new InvalidUserDataException(
                String.format(
                    "publication objects must implement the '%s' interface, implementation '%s' does not",
                    IvyPublicationInternal.class.getName(),
                    publication.getClass().getName()
                )
            );
        }
    }

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     */
    @Internal
    public IvyArtifactRepository getRepository() {
        return repository;
    }

    @Input
    @Optional
    Property<Credentials> getCredentials() {
        return credentials;
    }

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     */
    public void setRepository(IvyArtifactRepository repository) {
        this.repository = repository;
        this.credentials.set(((AuthenticationSupportedInternal) repository).getConfiguredCredentials());
    }

    @TaskAction
    public void publish() {
        IvyPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }

        IvyArtifactRepository repository = getRepository();
        if (repository == null) {
            throw new InvalidUserDataException("The 'repository' property is required");
        }
        getDuplicatePublicationTracker().checkCanPublish(publicationInternal, repository.getUrl(), repository.getName());

        doPublish(publicationInternal, repository);
    }

    @Inject
    protected IvyPublisher getIvyPublisher() {
        throw new UnsupportedOperationException();
    }

    private void doPublish(final IvyPublicationInternal publication, final IvyArtifactRepository repository) {
        new PublishOperation(publication, repository.getName()) {
            @Override
            protected void publish() {
                IvyNormalizedPublication normalizedPublication = publication.asNormalisedPublication();
                IvyPublisher publisher = getIvyPublisher();
                publisher.publish(normalizedPublication, repository);
            }
        }.run();
    }

    @Inject
    protected DuplicatePublicationTracker getDuplicatePublicationTracker() {
        throw new UnsupportedOperationException();
    }

}
