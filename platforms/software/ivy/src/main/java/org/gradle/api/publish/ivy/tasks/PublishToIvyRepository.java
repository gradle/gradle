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
import org.gradle.api.Incubating;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.RepositoryLayout;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.layout.AbstractRepositoryLayout;
import org.gradle.api.internal.provider.DefaultProvider;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyDuplicatePublicationTracker;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublisher;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.authentication.Authentication;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.instrumentation.api.annotations.ToBeReplacedByLazyProperty;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;
import java.net.URI;
import java.util.concurrent.Callable;

import static org.gradle.internal.serialization.Transient.varOf;

/**
 * Publishes an Ivy publication to an Ivy repository.
 *
 * @since 1.3
 */
@SuppressWarnings("this-escape")
@DisableCachingByDefault(because = "Not worth caching")
public abstract class PublishToIvyRepository extends DefaultTask {
    private final Transient.Var<IvyPublicationInternal> publication = varOf();

    @Deprecated
    private final Transient.Var<DefaultIvyArtifactRepository> repository = varOf();

    private final Cached<IvyNormalizedPublication> cachedNormalizedPublication = Cached.of(this::computeNormalizedPublication);

    /**
     * Creates a new {@code PublishToIvyRepository}.
     *
     * @since 1.3
     */
    @SuppressWarnings("this-escape")
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
     * @since 1.3
     */
    @Internal
    @ToBeReplacedByLazyProperty
    public IvyPublication getPublication() {
        return publication.get();
    }

    /**
     * Sets the publication to be published.
     *
     * @param publication The publication to be published
     * @since 1.3
     */
    public void setPublication(IvyPublication publication) {
        this.publication.set(toPublicationInternal(publication));
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
     * The name of the repository to publish to.
     *
     * @since 9.8.0
     */
    @Input
    @Incubating
    public abstract Property<String> getRepositoryName();

    /**
     * The URI of the repository to publish to.
     * <p>
     * May not be present if the repository has artifact or ivy patterns configured.
     *
     * @since 9.8.0
     */
    @Input
    @Optional
    @Incubating
    public abstract Property<URI> getRepositoryUri();

    /**
     * Whether to allow insecure protocols when publishing to the repository.
     *
     * @since 9.8.0
     */
    @Input
    @Incubating
    public abstract Property<Boolean> getAllowInsecureProtocol();

    /**
     * The authentication schemes to use when publishing to the repository.
     *
     * @since 9.8.0
     */
    @Nested
    @Incubating
    public abstract SetProperty<Authentication> getAuthenticationSchemes();

    /**
     * The credentials to use when publishing to the repository.
     *
     * @since 9.8.0
     */
    @Nested
    @Optional
    @Incubating
    public abstract Property<Credentials> getCredentials();

    /**
     * Additional artifact patterns to use when publishing to the repository.
     *
     * @since 9.8.0
     *
     * @see IvyArtifactRepository#artifactPattern(String)
     */
    @Input
    @Incubating
    public abstract SetProperty<String> getAdditionalArtifactPatterns();

    /**
     * Additional ivy patterns to use when publishing to the repository.
     *
     * @since 9.8.0
     *
     * @see IvyArtifactRepository#ivyPattern(String)
     */
    @Input
    @Incubating
    public abstract SetProperty<String> getAdditionalIvyPatterns();

    /**
     * The item organization layout to use when publishing to the repository.
     *
     * @since 9.8.0
     *
     * @see IvyArtifactRepository#layout(String)
     */
    @Nested
    @Incubating
    public abstract Property<RepositoryLayout> getRepositoryLayout();

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     *
     * @since 1.3
     *
     * @deprecated This method will be removed in Gradle 10.
     */
    @Internal
    @Deprecated
    public IvyArtifactRepository getRepository() {
        DeprecationLogger.deprecateMethod(PublishToIvyRepository.class, "getRepository")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_publish_repository")
            .nagUser();

        return repository.get();
    }

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     *
     * @since 1.3
     *
     * @deprecated This method will be removed in Gradle 10. Use {@link #configureFromRepository(IvyArtifactRepository)} instead.
     */
    @Deprecated
    public void setRepository(IvyArtifactRepository repository) {
        DeprecationLogger.deprecateMethod(PublishToIvyRepository.class, "setRepository")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "deprecate_publish_repository")
            .nagUser();

        configureFromRepository(repository);
    }

    /**
     * Configure this task to publish to the given repository.
     *
     * @param repository The repository to publish to.
     *
     * @since 9.8.0
     */
    @Incubating
    public void configureFromRepository(IvyArtifactRepository repository) {
        // We can stop calling `this.repository.set` in Gradle 10 and remove
        // the repository field.
        this.repository.set((DefaultIvyArtifactRepository) repository);

        DefaultIvyArtifactRepository repositoryInternal = (DefaultIvyArtifactRepository) repository;
        this.getRepositoryName().set(repository.getName());
        this.getRepositoryUri().set(new DefaultProvider<>(repository::getUrl));
        this.getAllowInsecureProtocol().set(new DefaultProvider<>(repository::isAllowInsecureProtocol));
        this.getAdditionalArtifactPatterns().set(new DefaultProvider<>(repositoryInternal::additionalArtifactPatterns));
        this.getAdditionalIvyPatterns().set(new DefaultProvider<>(repositoryInternal::additionalIvyPatterns));
        this.getRepositoryLayout().set(new DefaultProvider<>(repositoryInternal::getRepositoryLayout));
        this.getCredentials().set(repositoryInternal.getConfiguredCredentials());
        this.getAuthenticationSchemes().set(repositoryInternal.getConfiguredAuthenticationProvider());
    }

    /**
     * Publish.
     *
     * @since 1.3
     */
    @TaskAction
    public void publish() {
        IvyNormalizedPublication publication = cachedNormalizedPublication.get();
        IvyArtifactRepository repository = createRepository();
        getDuplicatePublicationTracker().checkCanPublish(publication, repository.getUrl(), repository.getName());

        IvyPublisher publisher = getIvyPublisher();
        try {
            publisher.publish(publication, repository);
        } catch (Exception e) {
            throw new PublishException(
                "Failed to publish publication '" + publication.getName() + "' to repository '" + repository.getName() + "'",
                e
            );
        }
    }

    private IvyNormalizedPublication computeNormalizedPublication() {
        IvyPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }
        return publicationInternal.asNormalisedPublication();
    }

    private IvyArtifactRepository createRepository() {
        DefaultIvyArtifactRepository repository = (DefaultIvyArtifactRepository) getServices().get(BaseRepositoryFactory.class).createIvyRepository();
        repository.setName(getRepositoryName().get());
        repository.setUrl(getRepositoryUri().getOrNull());
        getAdditionalArtifactPatterns().get().forEach(repository::artifactPattern);
        getAdditionalIvyPatterns().get().forEach(repository::ivyPattern);
        repository.setAllowInsecureProtocol(getAllowInsecureProtocol().get());
        repository.setRepositoryLayout((AbstractRepositoryLayout) getRepositoryLayout().get());
        Credentials credentials = getCredentials().getOrNull();
        if (credentials != null) {
            repository.setConfiguredCredentials(credentials);
        }
        repository.authentication(container -> container.addAll(getAuthenticationSchemes().get()));
        return repository;
    }

    @Inject
    protected abstract IvyPublisher getIvyPublisher();

    @Inject
    protected abstract IvyDuplicatePublicationTracker getDuplicatePublicationTracker();

}
