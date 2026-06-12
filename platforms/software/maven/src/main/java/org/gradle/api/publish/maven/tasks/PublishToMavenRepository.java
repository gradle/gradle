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
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.authentication.Authentication;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;
import org.gradle.work.DisableCachingByDefault;

import java.net.URI;

import static org.gradle.internal.serialization.Transient.varOf;

/**
 * Publishes a {@link MavenPublication} to a {@link MavenArtifactRepository}.
 *
 * @since 1.4
 */
@SuppressWarnings("this-escape")
@DisableCachingByDefault(because = "Not worth caching")
public abstract class PublishToMavenRepository extends AbstractPublishToMaven {

    @Deprecated
    private final Transient.Var<DefaultMavenArtifactRepository> repository = varOf();

    private final Cached<MavenNormalizedPublication> cachedNormalizedPublication = Cached.of(this::computeNormalizedPublication);

    /**
     * The name of the repository to publish to.
     *
     * @since 9.7.0
     */
    @Input
    @Incubating
    public abstract Property<String> getRepositoryName();

    /**
     * The URI of the repository to publish to.
     *
     * @since 9.7.0
     */
    @Input
    @Incubating
    public abstract Property<URI> getRepositoryUri();

    /**
     * Whether to allow insecure protocols when publishing to the repository.
     *
     * @since 9.7.0
     */
    @Input
    @Incubating
    public abstract Property<Boolean> getAllowInsecureProtocol();

    /**
     * The authentication schemes to use when publishing to the repository.
     *
     * @since 9.7.0
     */
    @Nested
    @Incubating
    public abstract SetProperty<Authentication> getAuthenticationSchemes();

    /**
     * The credentials to use when publishing to the repository.
     *
     * @since 9.7.0
     */
    @Nested
    @Optional
    @Incubating
    public abstract Property<Credentials> getCredentials();

    /**
     * The repository to publish to.
     *
     * @return The repository to publish to
     *
     * @deprecated This method will be removed in Gradle 10.
     */
    @Internal
    @Deprecated
    public MavenArtifactRepository getRepository() {
        DeprecationLogger.deprecateMethod(PublishToMavenRepository.class, "getRepository")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "publishing_deprecations")
            .nagUser();

        return repository.get();
    }

    /**
     * Get the listener manager.
     *
     * @deprecated This method will be removed in Gradle 10.
     */
    @Deprecated
    protected ListenerManager getListenerManager() {
        DeprecationLogger.deprecateMethod(PublishToMavenRepository.class, "getListenerManager")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "publishing_deprecations")
            .nagUser();

        return getServices().get(ListenerManager.class);
    }

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to
     *
     * @deprecated This method will be removed in Gradle 10. Use {@link #configureFromRepository(MavenArtifactRepository)} instead.
     */
    @Deprecated
    public void setRepository(MavenArtifactRepository repository) {
        DeprecationLogger.deprecateMethod(PublishToMavenRepository.class, "setRepository")
            .willBeRemovedInGradle10()
            .withUpgradeGuideSection(9, "publishing_deprecations")
            .nagUser();

        configureFromRepository(repository);
    }

    /**
     * Configure this task to publish to the given repository.
     *
     * @param repository The repository to publish to.
     *
     * @since 9.7.0
     */
    @Incubating
    public void configureFromRepository(MavenArtifactRepository repository) {
        this.repository.set((DefaultMavenArtifactRepository) repository);

        this.getRepositoryName().set(repository.getName());
        this.getRepositoryUri().set(repository.getUrl());
        this.getAllowInsecureProtocol().set(repository.isAllowInsecureProtocol());
        this.getCredentials().set(((DefaultMavenArtifactRepository) repository).getConfiguredCredentials());
        this.getAuthenticationSchemes().set(((DefaultMavenArtifactRepository) repository).getConfiguredAuthentication());
    }

    @TaskAction
    public void publish() {
        MavenNormalizedPublication publication = cachedNormalizedPublication.get();
        DefaultMavenArtifactRepository repository = createRepository();
        getDuplicatePublicationTracker().checkCanPublish(publication, repository.getUrl(), repository.getName());

        MavenPublisher mavenPublisher = new ValidatingMavenPublisher(getMavenPublishers().getRemotePublisher(getTemporaryDirFactory()));
        try {
            mavenPublisher.publish(publication, repository);
        } catch (Exception e) {
            throw new PublishException(
                "Failed to publish publication '" + publication.getName() + "' to repository '" + repository.getName() + "'",
                e
            );
        }
    }

    private MavenNormalizedPublication computeNormalizedPublication() {
        MavenPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }
        return publicationInternal.asNormalisedPublication();
    }

    private DefaultMavenArtifactRepository createRepository() {
        DefaultMavenArtifactRepository repository = (DefaultMavenArtifactRepository) getServices().get(BaseRepositoryFactory.class).createMavenRepository();
        repository.setName(getRepositoryName().get());
        repository.setUrl(getRepositoryUri().get());
        repository.setAllowInsecureProtocol(getAllowInsecureProtocol().get());
        Credentials credentials = getCredentials().getOrNull();
        if (credentials != null) {
            repository.setConfiguredCredentials(credentials);
        }
        repository.authentication(container -> container.addAll(getAuthenticationSchemes().get()));
        return repository;
    }

}
