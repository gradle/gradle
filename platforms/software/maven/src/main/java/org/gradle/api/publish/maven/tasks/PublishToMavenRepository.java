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
import org.gradle.api.credentials.Credentials;
import org.gradle.api.internal.GeneratedSubclasses;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.DefaultMavenArtifactRepository;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.publish.internal.PublishOperation;
import org.gradle.api.publish.maven.internal.publication.MavenPublicationInternal;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenPublisher;
import org.gradle.api.publish.maven.internal.publisher.ValidatingMavenPublisher;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import org.gradle.authentication.Authentication;
import org.gradle.internal.instrumentation.api.annotations.NotToBeReplacedByLazyProperty;
import org.gradle.internal.serialization.Cached;
import org.gradle.internal.serialization.Transient;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.work.DisableCachingByDefault;

import java.net.URI;
import java.util.Collection;

import static org.gradle.internal.serialization.Transient.varOf;

/**
 * Publishes a {@link org.gradle.api.publish.maven.MavenPublication} to a {@link MavenArtifactRepository}.
 *
 * @since 1.4
 */
@DisableCachingByDefault(because = "Not worth caching")
public abstract class PublishToMavenRepository extends AbstractPublishToMaven {
    private final Transient.Var<DefaultMavenArtifactRepository> repository = varOf();
    private final Cached<PublishSpec> spec = Cached.of(this::computeSpec);

    /**
     * The repository to publish to.
     *
     * For now, only instances of {@link DefaultMavenArtifactRepository} are supported.
     */
    @Internal
    @NotToBeReplacedByLazyProperty(because = "we need a better way to handle this, see https://github.com/gradle/gradle/pull/30665#pullrequestreview-2329667058")
    public MavenArtifactRepository getRepository() {
        return repository.get();
    }

    @Nested
    @Optional
    abstract Property<Credentials> getCredentials();

    /**
     * Sets the repository to publish to.
     *
     * @param repository The repository to publish to. Only instances of {@link DefaultMavenArtifactRepository} are supported.
     */
    public void setRepository(MavenArtifactRepository repository) {
        this.repository.set((DefaultMavenArtifactRepository) repository);
        this.getCredentials().set(((DefaultMavenArtifactRepository) repository).getConfiguredCredentials());
    }

    @TaskAction
    public void publish() {
        PublishSpec spec = this.spec.get();
        MavenNormalizedPublication publication = spec.publication;
        MavenArtifactRepository repository = spec.repository.get(getServices());
        getDuplicatePublicationTracker().checkCanPublish(publication, repository.getUrl(), repository.getName());
        doPublish(publication, repository);
    }

    private PublishSpec computeSpec() {
        MavenPublicationInternal publicationInternal = getPublicationInternal();
        if (publicationInternal == null) {
            throw new InvalidUserDataException("The 'publication' property is required");
        }

        DefaultMavenArtifactRepository repository = (DefaultMavenArtifactRepository) getRepository();
        if (repository == null) {
            throw new InvalidUserDataException("The 'repository' property is required");
        }
        MavenNormalizedPublication normalizedPublication = publicationInternal.asNormalisedPublication();
        return new PublishSpec(
                RepositorySpec.of(repository),
                normalizedPublication
        );
    }

    private void doPublish(final MavenNormalizedPublication normalizedPublication, final MavenArtifactRepository repository) {
        new PublishOperation(normalizedPublication.getName(), repository.getName()) {
            @Override
            protected void publish() {
                validatingMavenPublisher().publish(normalizedPublication, repository);
            }
        }.run();
    }

    private MavenPublisher validatingMavenPublisher() {
        return new ValidatingMavenPublisher(
                getMavenPublishers().getRemotePublisher(getTemporaryDirFactory())
        );
    }

    static class PublishSpec {

        private final RepositorySpec repository;
        private final MavenNormalizedPublication publication;

        public PublishSpec(
                RepositorySpec repository,
                MavenNormalizedPublication publication
        ) {
            this.repository = repository;
            this.publication = publication;
        }
    }

    static abstract class RepositorySpec {

        static RepositorySpec of(DefaultMavenArtifactRepository repository) {
            return new Configured(repository);
        }

        abstract MavenArtifactRepository get(ServiceRegistry services);

        static class Configured extends RepositorySpec implements java.io.Serializable {
            final DefaultMavenArtifactRepository repository;

            public Configured(DefaultMavenArtifactRepository repository) {
                this.repository = repository;
            }

            @Override
            MavenArtifactRepository get(ServiceRegistry services) {
                return repository;
            }

            private Object writeReplace() {
                CredentialsSpec credentialsSpec = repository.getConfiguredCredentials().map(it -> CredentialsSpec.of(repository.getName(), it)).getOrNull();
                return new DefaultRepositorySpec(repository.getName(), repository.getUrl(), repository.isAllowInsecureProtocol(), credentialsSpec, repository.getConfiguredAuthentication());
            }
        }

        static class DefaultRepositorySpec extends RepositorySpec {
            private final URI repositoryUrl;
            private final CredentialsSpec credentials;
            private final boolean allowInsecureProtocol;
            private final String name;
            private final Collection<Authentication> authentications;

            public DefaultRepositorySpec(String name, URI repositoryUrl, boolean allowInsecureProtocol, CredentialsSpec credentials, Collection<Authentication> authentications) {
                this.name = name;
                this.repositoryUrl = repositoryUrl;
                this.allowInsecureProtocol = allowInsecureProtocol;
                this.credentials = credentials;
                this.authentications = authentications;
            }
            @Override
            MavenArtifactRepository get(ServiceRegistry services) {
                DefaultMavenArtifactRepository repository = (DefaultMavenArtifactRepository) services.get(BaseRepositoryFactory.class).createMavenRepository();
                repository.setName(name);
                repository.setUrl(repositoryUrl);
                repository.setAllowInsecureProtocol(allowInsecureProtocol);
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
}
