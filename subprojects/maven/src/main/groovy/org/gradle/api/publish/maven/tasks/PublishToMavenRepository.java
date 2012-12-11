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

import org.gradle.api.Buildable;
import org.gradle.api.DefaultTask;
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.ArtifactPublicationServices;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.publication.maven.internal.*;
import org.gradle.api.publication.maven.internal.ant.NoInstallDeployTaskFactory;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.MavenPublisher;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.Factory;
import org.gradle.logging.LoggingManagerInternal;

import javax.inject.Inject;
import java.io.File;
import java.util.concurrent.Callable;

/**
 * Publishes a {@link org.gradle.api.publish.maven.MavenPublication} to a {@link MavenArtifactRepository}.
 *
 * @since 1.4
 */
@Incubating
public class PublishToMavenRepository extends DefaultTask {

    private MavenPublicationInternal publication;
    private MavenArtifactRepository repository;

    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final FileResolver fileResolver;
    private final ArtifactPublicationServices publicationServices;

    @Inject
    public PublishToMavenRepository(ArtifactPublicationServices publicationServices, Factory<LoggingManagerInternal> loggingManagerFactory, FileResolver fileResolver) {
        this.loggingManagerFactory = loggingManagerFactory;
        this.fileResolver = fileResolver;
        this.publicationServices = publicationServices;


        // Allow the publication to participate in incremental build
        getInputs().files(new Callable<FileCollection>() {
            public FileCollection call() throws Exception {
                MavenPublicationInternal publicationInternal = getPublicationInternal();
                return publicationInternal == null ? null : publicationInternal.getPublishableFiles();
            }
        });

        // Allow the publication to have its dependencies fulfilled
        // There may be dependencies that aren't about creating files and not covered above
        dependsOn(new Callable<Buildable>() {
            public Buildable call() throws Exception {
                MavenPublicationInternal publicationInternal = getPublicationInternal();
                return publicationInternal == null ? null : publicationInternal;
            }
        });

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
    public MavenPublication getPublication() {
        return publication;
    }

    /**
     * Sets the publication to be published.
     *
     * @param publication The publication to be published
     */
    public void setPublication(MavenPublication publication) {
        this.publication = toPublicationInternal(publication);
    }

    private MavenPublicationInternal getPublicationInternal() {
        return toPublicationInternal(getPublication());
    }

    private static MavenPublicationInternal toPublicationInternal(MavenPublication publication) {
        if (publication == null) {
            return null;
        } else if (publication instanceof MavenPublicationInternal) {
            return (MavenPublicationInternal) publication;
        } else {
            throw new InvalidUserDataException(
                    String.format(
                            "publication objects must implement the '%s' interface, implementation '%s' does not",
                            MavenPublicationInternal.class.getName(),
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

        doPublish(publicationInternal, repository);
    }

    private void doPublish(final MavenPublicationInternal publication, final MavenArtifactRepository repository) {
        new PublishOperation(publication, repository) {
            @Override
            protected void publish() throws Exception {
                Factory<Configuration> configurationFactory = new Factory<Configuration>() {
                    public Configuration create() {
                        return getProject().getConfigurations().detachedConfiguration();
                    }
                };
                MavenPublisher publisher = new MavenPublisher(createDeployerFactory(), configurationFactory, publicationServices.createArtifactPublisher());
                MavenNormalizedPublication normalizedPublication = publication.asNormalisedPublication();
                publisher.publish(normalizedPublication, repository);
            }
        }.run();
    }

    private DeployerFactory createDeployerFactory() {
        return new CustomTaskFactoryDeployerFactory(
                new DefaultMavenFactory(),
                loggingManagerFactory,
                fileResolver,
                new MavenPomMetaInfoProvider() {
                    public File getMavenPomDir() {
                        return publication.getPomDir();
                    }
                },
                getProject().getConfigurations(), // these won't actually be used, but it's the easiest way to get a ConfigurationContainer.
                new DefaultConf2ScopeMappingContainer(),
                new NoInstallDeployTaskFactory(getTemporaryDirFactory())
        );
    }

}
