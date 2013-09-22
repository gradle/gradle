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

package org.gradle.api.internal.artifacts.repositories;

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.*;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.ModuleMetadataProcessor;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.LatestStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionMatcher;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.legacy.FixedResolverArtifactRepository;
import org.gradle.api.internal.artifacts.repositories.legacy.LegacyDependencyResolverRepositoryFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.util.ConfigureUtil;

import java.io.File;
import java.util.Map;

public class DefaultBaseRepositoryFactory implements BaseRepositoryFactory {
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileResolver fileResolver;
    private final Instantiator instantiator;
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;
    private final ModuleMetadataProcessor metadataProcessor;
    private final LegacyDependencyResolverRepositoryFactory legacyDependencyResolverRepositoryFactory;
    private final VersionMatcher versionMatcher;
    private final LatestStrategy latestStrategy;
    private final ResolverStrategy resolverStrategy;

    public DefaultBaseRepositoryFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                        FileResolver fileResolver,
                                        Instantiator instantiator,
                                        RepositoryTransportFactory transportFactory,
                                        LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                                        ModuleMetadataProcessor metadataProcessor,
                                        LegacyDependencyResolverRepositoryFactory legacyDependencyResolverRepositoryFactory,
                                        VersionMatcher versionMatcher,
                                        LatestStrategy latestStrategy,
                                        ResolverStrategy resolverStrategy) {
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileResolver = fileResolver;
        this.instantiator = instantiator;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.metadataProcessor = metadataProcessor;
        this.legacyDependencyResolverRepositoryFactory = legacyDependencyResolverRepositoryFactory;
        this.versionMatcher = versionMatcher;
        this.latestStrategy = latestStrategy;
        this.resolverStrategy = resolverStrategy;
    }

    public ArtifactRepository createRepository(Object userDescription) {
        if (userDescription instanceof ArtifactRepository) {
            return (ArtifactRepository) userDescription;
        }

        if (userDescription instanceof String) {
            MavenArtifactRepository repository = createMavenRepository();
            repository.setUrl(userDescription);
            return repository;
        } else if (userDescription instanceof Map) {
            Map<String, ?> userDescriptionMap = (Map<String, ?>) userDescription;
            MavenArtifactRepository repository = createMavenRepository();
            ConfigureUtil.configureByMap(userDescriptionMap, repository);
            return repository;
        }

        if (userDescription instanceof DependencyResolver) {
            return legacyDependencyResolverRepositoryFactory.createRepository((DependencyResolver) userDescription);
        }
        throw new InvalidUserDataException(String.format("Cannot create a DependencyResolver instance from %s", userDescription));
    }

    public FlatDirectoryArtifactRepository createFlatDirRepository() {
        return instantiator.newInstance(DefaultFlatDirArtifactRepository.class, fileResolver, transportFactory,
                locallyAvailableResourceFinder, metadataProcessor, resolverStrategy, versionMatcher, latestStrategy);
    }

    public MavenArtifactRepository createMavenLocalRepository() {
        MavenArtifactRepository mavenRepository = instantiator.newInstance(DefaultMavenLocalArtifactRepository.class, fileResolver, createPasswordCredentials(), transportFactory,
                locallyAvailableResourceFinder, metadataProcessor, versionMatcher, latestStrategy, resolverStrategy);
        final File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
        mavenRepository.setUrl(localMavenRepository);
        return mavenRepository;
    }

    public MavenArtifactRepository createJCenterRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository();
        mavenRepository.setUrl(DefaultRepositoryHandler.BINTRAY_JCENTER_URL);
        return mavenRepository;
    }

    public MavenArtifactRepository createMavenCentralRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository();
        mavenRepository.setUrl(RepositoryHandler.MAVEN_CENTRAL_URL);
        return mavenRepository;
    }

    public IvyArtifactRepository createIvyRepository() {
        return instantiator.newInstance(DefaultIvyArtifactRepository.class, fileResolver, createPasswordCredentials(), transportFactory,
                locallyAvailableResourceFinder, instantiator, metadataProcessor, versionMatcher, latestStrategy, resolverStrategy);
    }

    public MavenArtifactRepository createMavenRepository() {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, fileResolver, createPasswordCredentials(), transportFactory,
                locallyAvailableResourceFinder, metadataProcessor, versionMatcher, latestStrategy, resolverStrategy
        );
    }

    public DependencyResolver toResolver(ArtifactRepository repository) {
        return ((ArtifactRepositoryInternal) repository).createLegacyDslObject();
    }

    public FixedResolverArtifactRepository createResolverBackedRepository(DependencyResolver resolver) {
        return new FixedResolverArtifactRepository(resolver);
    }

    private PasswordCredentials createPasswordCredentials() {
        return instantiator.newInstance(DefaultPasswordCredentials.class);
    }
}
