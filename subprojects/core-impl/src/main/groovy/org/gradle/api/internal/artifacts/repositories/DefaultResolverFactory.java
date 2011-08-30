/*
 * Copyright 2007 the original author or authors.
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

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.*;
import org.gradle.api.artifacts.maven.*;
import org.gradle.api.internal.Factory;
import org.gradle.api.internal.Instantiator;
import org.gradle.api.internal.artifacts.ResolverFactory;
import org.gradle.api.internal.artifacts.publish.maven.*;
import org.gradle.api.internal.artifacts.publish.maven.deploy.BaseMavenInstaller;
import org.gradle.api.internal.artifacts.publish.maven.deploy.BasePomFilterContainer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.DefaultArtifactPomContainer;
import org.gradle.api.internal.artifacts.publish.maven.deploy.groovy.DefaultGroovyMavenDeployer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.util.ConfigureUtil;

import java.util.Map;

/**
 * @author Hans Dockter
 */
public class DefaultResolverFactory implements ResolverFactory {
    private final Factory<LoggingManagerInternal> loggingManagerFactory;
    private final MavenFactory mavenFactory;
    private final LocalMavenCacheLocator localMavenCacheLocator;
    private final FileResolver fileResolver;
    private final Instantiator instantiator;

    public DefaultResolverFactory(Factory<LoggingManagerInternal> loggingManagerFactory, MavenFactory mavenFactory, LocalMavenCacheLocator localMavenCacheLocator, FileResolver fileResolver, Instantiator instantiator) {
        this.loggingManagerFactory = loggingManagerFactory;
        this.mavenFactory = mavenFactory;
        this.localMavenCacheLocator = localMavenCacheLocator;
        this.fileResolver = fileResolver;
        this.instantiator = instantiator;
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
        
        DependencyResolver result;
        if (userDescription instanceof DependencyResolver) {
            result = (DependencyResolver) userDescription;
        } else {
            throw new InvalidUserDataException(String.format("Cannot create a DependencyResolver instance from %s", userDescription));
        }
        return new FixedResolverArtifactRepository(result);
    }

    public FlatDirectoryArtifactRepository createFlatDirRepository() {
        return instantiator.newInstance(DefaultFlatDirArtifactRepository.class, fileResolver);
    }

    public MavenArtifactRepository createMavenLocalRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository();
        mavenRepository.setUrl(localMavenCacheLocator.getLocalMavenCache());
        return mavenRepository;
    }

    public MavenArtifactRepository createMavenCentralRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository();
        mavenRepository.setUrl(RepositoryHandler.MAVEN_CENTRAL_URL);
        return mavenRepository;
    }

    // todo use MavenPluginConvention pom factory after modularization is done

    public GroovyMavenDeployer createMavenDeployer(MavenPomMetaInfoProvider pomMetaInfoProvider,
                                                   ConfigurationContainer configurationContainer,
                                                   Conf2ScopeMappingContainer scopeMapping, FileResolver fileResolver) {
        PomFilterContainer pomFilterContainer = createPomFilterContainer(
                mavenFactory.createMavenPomFactory(configurationContainer, scopeMapping, fileResolver));
        return new DefaultGroovyMavenDeployer(pomFilterContainer, createArtifactPomContainer(
                pomMetaInfoProvider, pomFilterContainer, createArtifactPomFactory()), loggingManagerFactory.create());
    }

    // todo use MavenPluginConvention pom factory after modularization is done

    public MavenResolver createMavenInstaller(MavenPomMetaInfoProvider pomMetaInfoProvider,
                                              ConfigurationContainer configurationContainer,
                                              Conf2ScopeMappingContainer scopeMapping, FileResolver fileResolver) {
        PomFilterContainer pomFilterContainer = createPomFilterContainer(
                mavenFactory.createMavenPomFactory(configurationContainer, scopeMapping, fileResolver));
        return new BaseMavenInstaller(pomFilterContainer, createArtifactPomContainer(pomMetaInfoProvider,
                pomFilterContainer, createArtifactPomFactory()), loggingManagerFactory.create());
    }

    public IvyArtifactRepository createIvyRepository() {
        return instantiator.newInstance(DefaultIvyArtifactRepository.class, fileResolver);
    }

    public MavenArtifactRepository createMavenRepository() {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, fileResolver);
    }

    private PomFilterContainer createPomFilterContainer(Factory<MavenPom> mavenPomFactory) {
        return new BasePomFilterContainer(mavenPomFactory);
    }

    private ArtifactPomFactory createArtifactPomFactory() {
        return new DefaultArtifactPomFactory();
    }

    private ArtifactPomContainer createArtifactPomContainer(MavenPomMetaInfoProvider pomMetaInfoProvider, PomFilterContainer filterContainer,
                                                           ArtifactPomFactory pomFactory) {
        return new DefaultArtifactPomContainer(pomMetaInfoProvider, filterContainer, pomFactory);
    }

}
