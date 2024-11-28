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
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultIvyArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.layout.AbstractRepositoryLayout;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.ivy.IvyPublication;
import org.gradle.api.publish.ivy.internal.publication.IvyPublicationInternal;
import org.gradle.api.publish.ivy.internal.publisher.IvyDuplicatePublicationTracker;
import org.gradle.api.publish.ivy.internal.publisher.IvyNormalizedPublication;
import org.gradle.api.publish.ivy.internal.publisher.IvyPublisher;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.authentication.Authentication;
import org.gradle.internal.Cast;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.work.DisableCachingByDefault;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.net.URI;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Publishes an IvyPublication to an IvyArtifactRepository.
 *
 * @since 1.3
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class PublishToIvyRepository extends DefaultTask {
    private final Transient.Var<IvyPublication> publication = Transient.varOf();
    private final Transient.Var<IvyArtifactRepository> repository = Transient.varOf();
    private final Cached<PublishSpec> spec = Cached.of(this::computeSpec);

    public PublishToIvyRepository() {

        // Allow the publication to participate in incremental build
        getInputs()
            .files((Callable<FileCollection>) () ->
                toPublicationInternal(getPublication()).getPublishableArtifacts().getFiles()
            ).withPropertyName("publication.publishableFiles")
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
     * Currently only instances of IvyPublication are supported.
     */
    @Internal
    @NotToBeReplacedByLazyProperty(because = "we need a better way to handle this, see https://github.com/gradle/gradle/pull/30665#pullrequestreview-2329667058")
    public IvyPublication getPublication() {
        return publication.get();
    }

    /**
     * Sets the publication to be published.
     *
     * @param publication The publication to be published. Currently only instances of IvyPublication are supported.
     */
    public void setPublication(IvyPublication publication) {
        this.publication.set(toPublicationInternal(publication));
    }

    private static IvyPublicationInternal toPublicationInternal(IvyPublication publication) {
        if (publication instanceof IvyPublicationInternal) {
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
     * Only instances of DefaultIvyArtifactRepository are supported
     */
    @Internal
    @NotToBeReplacedByLazyProperty(because = "we need a better way to handle this, see https://github.com/gradle/gradle/pull/30665#pullrequestreview-2329667058")
    public IvyArtifactRepository getRepository() {
        return repository.get();
    }

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to. Only instances of DefaultIvyArtifactRepository are supported
     */
    @SuppressWarnings("RedundantCast") // this cast is intentional to fail earlier
    public void setRepository(IvyArtifactRepository repository) {
        this.repository.set((DefaultIvyArtifactRepository) repository);
        this.getCredentials().set(((DefaultIvyArtifactRepository) repository).getConfiguredCredentials());
    }

    @Nested
    @Optional
    abstract Property<Credentials> getCredentials();

    @TaskAction
    public void publish() {
        PublishSpec spec = this.spec.get();
        IvyNormalizedPublication publication = spec.publication;
        IvyArtifactRepository repository = spec.repository.get(getServices());
        getDuplicatePublicationTracker().checkCanPublish(publication, repository.getUrl().getOrNull(), repository.getName());
        doPublish(publication, repository);
    }

    private PublishSpec computeSpec() {
        IvyPublicationInternal publicationInternal = toPublicationInternal(getPublication());
        DefaultIvyArtifactRepository repository = Cast.cast(DefaultIvyArtifactRepository.class, getRepository());
        IvyNormalizedPublication normalizedPublication = publicationInternal.asNormalisedPublication();
        return new PublishSpec(
            RepositorySpec.of(repository),
            normalizedPublication
        );
    }

    @Inject
    protected abstract IvyPublisher getIvyPublisher();

    private void doPublish(final IvyNormalizedPublication normalizedPublication, final IvyArtifactRepository repository) {
        new PublishOperation(normalizedPublication.getName(), repository.getName()) {
            @Override
            protected void publish() {
                IvyPublisher publisher = getIvyPublisher();
                publisher.publish(normalizedPublication, repository);
            }
        }.run();
    }

    static class PublishSpec {

        private final RepositorySpec repository;
        private final IvyNormalizedPublication publication;

        public PublishSpec(
            RepositorySpec repository,
            IvyNormalizedPublication publication
        ) {
            this.repository = repository;
            this.publication = publication;
        }
    }

    static abstract class RepositorySpec {

        static RepositorySpec of(DefaultIvyArtifactRepository repository) {
            return new Configured(repository);
        }

        abstract IvyArtifactRepository get(ServiceRegistry services);

        static class Configured extends RepositorySpec implements java.io.Serializable {
            final DefaultIvyArtifactRepository repository;

            public Configured(DefaultIvyArtifactRepository repository) {
                this.repository = repository;
            }

            @Override
            IvyArtifactRepository get(ServiceRegistry services) {
                return repository;
            }

            private Object writeReplace() {
                return new DefaultRepositorySpec(
                    repository.getName(),
                    repository.getUrl().getOrNull(),
                    repository.getAllowInsecureProtocol().get(),
                    credentialsSpec(),
                    repository.getRepositoryLayout(),
                    repository.additionalArtifactPatterns(),
                    repository.additionalIvyPatterns(),
                    repository.getConfiguredAuthentication());
            }

            @Nullable
            private CredentialsSpec credentialsSpec() {
                return repository.getConfiguredCredentials().map(
                    credentials -> CredentialsSpec.of(repository.getName(), credentials)
                ).getOrNull();
            }
        }

        static class DefaultRepositorySpec extends RepositorySpec {
            private final URI repositoryUrl;
            private final CredentialsSpec credentials;
            private final AbstractRepositoryLayout layout;
            private final boolean allowInsecureProtocol;
            private final String name;
            private final Set<String> artifactPatterns;
            private final Set<String> ivyPatterns;
            private final Collection<Authentication> authentications;

            public DefaultRepositorySpec(String name, URI repositoryUrl, boolean allowInsecureProtocol, CredentialsSpec credentials, AbstractRepositoryLayout layout, Set<String> artifactPatterns, Set<String> ivyPatterns, Collection<Authentication> authentications) {
                this.name = name;
                this.repositoryUrl = repositoryUrl;
                this.allowInsecureProtocol = allowInsecureProtocol;
                this.credentials = credentials;
                this.layout = layout;
                this.artifactPatterns = artifactPatterns;
                this.ivyPatterns = ivyPatterns;
                this.authentications = authentications;
            }

            @Override
            IvyArtifactRepository get(ServiceRegistry services) {
                DefaultIvyArtifactRepository repository = (DefaultIvyArtifactRepository) services.get(BaseRepositoryFactory.class).createIvyRepository();
                repository.setName(name);
                repository.getUrl().set(repositoryUrl);
                artifactPatterns.forEach(repository::artifactPattern);
                ivyPatterns.forEach(repository::ivyPattern);
                repository.getAllowInsecureProtocol().set(allowInsecureProtocol);
                repository.setRepositoryLayout(layout);
                if (credentials != null) {
                    Provider<? extends Credentials> provider = services.get(ProviderFactory.class).credentials(credentials.getType(), name);
                    repository.setConfiguredCredentials(provider.get());
                }
                repository.authentication(container -> container.addAll(authentications));
                return repository;
            }
        }

        static class CredentialsSpec {
            private final String identity;
            private final Class<? extends Credentials> type;

            private CredentialsSpec(String identity, Class<? extends Credentials> type) {
                this.identity = identity;
                this.type = type;
            }

            @SuppressWarnings("unchecked")
            public static CredentialsSpec of(String identity, Credentials credentials) {
                return new CredentialsSpec(identity, (Class<? extends Credentials>) GeneratedSubclasses.unpackType(credentials));
            }

            public Class<? extends Credentials> getType() {
                return type;
            }

            public String getIdentity() {
                return identity;
            }
        }
    }

    @Inject
    protected abstract IvyDuplicatePublicationTracker getDuplicatePublicationTracker();
}
