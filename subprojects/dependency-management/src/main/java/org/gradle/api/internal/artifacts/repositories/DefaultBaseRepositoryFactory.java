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

import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.io.File;
import java.util.Map;

public class DefaultBaseRepositoryFactory implements BaseRepositoryFactory {
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileResolver fileResolver;
    private final Instantiator instantiator;
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser;
    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;
    private final IvyContextManager ivyContextManager;

    public DefaultBaseRepositoryFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                        FileResolver fileResolver,
                                        Instantiator instantiator,
                                        RepositoryTransportFactory transportFactory,
                                        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                        MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                        AuthenticationSchemeRegistry authenticationSchemeRegistry,
                                        IvyContextManager ivyContextManager) {
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileResolver = fileResolver;
        this.instantiator = instantiator;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.pomParser = pomParser;
        this.authenticationSchemeRegistry = authenticationSchemeRegistry;
        this.ivyContextManager = ivyContextManager;
    }

    public FlatDirectoryArtifactRepository createFlatDirRepository() {
        return instantiator.newInstance(DefaultFlatDirArtifactRepository.class, fileResolver, transportFactory,
                locallyAvailableResourceFinder, artifactFileStore, ivyContextManager);
    }

    public MavenArtifactRepository createMavenLocalRepository() {
        MavenArtifactRepository mavenRepository = instantiator.newInstance(DefaultMavenLocalArtifactRepository.class, fileResolver, transportFactory,
                locallyAvailableResourceFinder, instantiator, artifactFileStore, pomParser, createAuthenticationContainer());
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
        return instantiator.newInstance(DefaultIvyArtifactRepository.class, fileResolver, transportFactory,
                locallyAvailableResourceFinder, instantiator, artifactFileStore, createAuthenticationContainer(), ivyContextManager);
    }

    public MavenArtifactRepository createMavenRepository() {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, fileResolver, transportFactory,
                locallyAvailableResourceFinder, instantiator, artifactFileStore, pomParser, createAuthenticationContainer());
    }

    protected AuthenticationContainer createAuthenticationContainer() {
        DefaultAuthenticationContainer container = instantiator.newInstance(DefaultAuthenticationContainer.class, instantiator);

        for (Map.Entry<Class<Authentication>, Class<? extends Authentication>> e : authenticationSchemeRegistry.getRegisteredSchemes().entrySet()) {
            container.registerBinding(e.getKey(), e.getValue());
        }

        return container;
    }
}
