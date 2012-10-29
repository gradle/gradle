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

package org.gradle.api.publish;

import org.gradle.api.Buildable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.repositories.ArtifactRepositoryInternal;
import org.gradle.api.publish.internal.NormalizedPublication;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.internal.Publisher;
import org.gradle.api.tasks.TaskAction;

import java.util.concurrent.Callable;

/**
 * Publishes a {@link Publication} to an {@link ArtifactRepository}.
 */
@Incubating
public class Publish extends DefaultTask {

    private PublicationInternal publication;
    private ArtifactRepositoryInternal repository;

    public Publish() {
        // Allow the publication to participate in incremental build
        getInputs().files(new Callable<FileCollection>() {
            public FileCollection call() throws Exception {
                PublicationInternal publicationInternal = getPublicationInternal();
                return publicationInternal == null ? null : publicationInternal.getPublishableFiles();
            }
        });

        // Allow the publication to have its dependencies fulfilled
        // There may be dependencies that aren't about creating files and not covered above
        dependsOn(new Callable<Buildable>() {
            public Buildable call() throws Exception {
                PublicationInternal publicationInternal = getPublicationInternal();
                return publicationInternal == null ? null : publicationInternal;
            }
        });

        // Should repositories be able to participate in incremental?
        // At the least, they may be able to express themselves as output files
        // They *might* have input files and other dependencies as well though
        // Inputs: The credentials they need may be expressed in a file
        // Dependencies: Can't think of a case here
    }

    public Publication getPublication() {
        return publication;
    }

    protected PublicationInternal getPublicationInternal() {
        return toPublicationInternal(getPublication());
    }

    public void setPublication(Publication publication) {
        this.publication = toPublicationInternal(publication);
    }

    private static PublicationInternal toPublicationInternal(Publication publication) {
        if (publication == null) {
            return null;
        } else if (publication instanceof PublicationInternal) {
            return (PublicationInternal) publication;
        } else {
            throw new InvalidUserDataException(
                    String.format(
                            "publication objects must implement the '%s' interface, implementation '%s' does not",
                            PublicationInternal.class.getName(),
                            publication.getClass().getName()
                    )
            );
        }
    }

    public ArtifactRepository getRepository() {
        return repository;
    }

    public ArtifactRepositoryInternal getRepositoryInternal() {
        return toRepositoryInternal(getRepository());
    }

    public void setRepository(ArtifactRepository repository) {
        this.repository = toRepositoryInternal(repository);
    }

    private static ArtifactRepositoryInternal toRepositoryInternal(ArtifactRepository repository) {
        if (repository == null) {
            return null;
        } else if (repository instanceof ArtifactRepositoryInternal) {
            return (ArtifactRepositoryInternal) repository;
        } else {
            throw new InvalidUserDataException(
                    String.format(
                            "repository objects must implement the '%s' interface, implementation '%s' does not",
                            ArtifactRepositoryInternal.class.getName(),
                            repository.getClass().getName()
                    )
            );
        }
    }

    @TaskAction
    public void publish() {
        PublicationInternal<?> publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }

        ArtifactRepositoryInternal repositoryInternal = getRepositoryInternal();
        if (repositoryInternal == null) {
            throw new InvalidUserDataException("The 'repository' property is required");
        }

        doPublish(publicationInternal, repositoryInternal);
    }

    private <T extends NormalizedPublication> void doPublish(PublicationInternal<T> publication, ArtifactRepositoryInternal repository) {
        Class<T> normalizedPublicationType = publication.getNormalisedPublicationType();
        Publisher<T> publisher = repository.createPublisher(normalizedPublicationType);

        // If it wasn't for convention mapping, we could hoist this check up to configuration time
        // But we can't really know when publication or repository changes to run this check
        if (publisher == null) {
            throw new InvalidUserDataException(
                    String.format("Repository '%s' cannot publish publication '%s'", repository, publication)
            );
        }

        T normalizedPublication = publication.asNormalisedPublication();
        publisher.publish(normalizedPublication);
    }

}
