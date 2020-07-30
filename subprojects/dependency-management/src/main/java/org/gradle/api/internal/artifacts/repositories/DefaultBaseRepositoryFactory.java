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

import org.gradle.api.Transformer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.io.File;
import java.net.URI;
import java.util.Map;

public class DefaultBaseRepositoryFactory implements BaseRepositoryFactory {
    private final LocalMavenRepositoryLocator localMavenRepositoryLocator;
    private final FileResolver fileResolver;
    private final Instantiator instantiator;
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final FileStore<String> externalResourcesFileStore;
    private final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser;
    private final FileCollectionFactory fileCollectionFactory;
    private final GradleModuleMetadataParser metadataParser;
    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;
    private final IvyContextManager ivyContextManager;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final FileResourceRepository fileResourceRepository;
    private final MavenMutableModuleMetadataFactory mavenMetadataFactory;
    private final IvyMutableModuleMetadataFactory ivyMetadataFactory;
    private final IsolatableFactory isolatableFactory;
    private final ObjectFactory objectFactory;
    private final CollectionCallbackActionDecorator callbackActionDecorator;
    private final DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory;
    private final ChecksumService checksumService;
    private final ProviderFactory providerFactory;

    public DefaultBaseRepositoryFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                        FileResolver fileResolver,
                                        FileCollectionFactory fileCollectionFactory,
                                        RepositoryTransportFactory transportFactory,
                                        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                        FileStore<String> externalResourcesFileStore,
                                        MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                        GradleModuleMetadataParser metadataParser,
                                        AuthenticationSchemeRegistry authenticationSchemeRegistry,
                                        IvyContextManager ivyContextManager,
                                        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                        InstantiatorFactory instantiatorFactory,
                                        FileResourceRepository fileResourceRepository,
                                        MavenMutableModuleMetadataFactory mavenMetadataFactory,
                                        IvyMutableModuleMetadataFactory ivyMetadataFactory,
                                        IsolatableFactory isolatableFactory,
                                        ObjectFactory objectFactory,
                                        CollectionCallbackActionDecorator callbackActionDecorator,
                                        DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
                                        ChecksumService checksumService,
                                        ProviderFactory providerFactory) {
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileResolver = fileResolver;
        this.fileCollectionFactory = fileCollectionFactory;
        this.metadataParser = metadataParser;
        this.instantiator = instantiatorFactory.decorateLenient();
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.externalResourcesFileStore = externalResourcesFileStore;
        this.pomParser = pomParser;
        this.authenticationSchemeRegistry = authenticationSchemeRegistry;
        this.ivyContextManager = ivyContextManager;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.fileResourceRepository = fileResourceRepository;
        this.mavenMetadataFactory = mavenMetadataFactory;
        this.ivyMetadataFactory = ivyMetadataFactory;
        this.isolatableFactory = isolatableFactory;
        this.objectFactory = objectFactory;
        this.callbackActionDecorator = callbackActionDecorator;
        this.urlArtifactRepositoryFactory = urlArtifactRepositoryFactory;
        this.checksumService = checksumService;
        this.providerFactory = providerFactory;
    }

    @Override
    public FlatDirectoryArtifactRepository createFlatDirRepository() {
        return instantiator.newInstance(DefaultFlatDirArtifactRepository.class, fileCollectionFactory, transportFactory, locallyAvailableResourceFinder, artifactFileStore, ivyMetadataFactory, instantiatorFactory, objectFactory, checksumService);
    }

    @Override
    public ArtifactRepository createGradlePluginPortal() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(PLUGIN_PORTAL_DEFAULT_URL));
        mavenRepository.setUrl(System.getProperty(PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY, PLUGIN_PORTAL_DEFAULT_URL));
        mavenRepository.metadataSources(MavenArtifactRepository.MetadataSources::mavenPom);
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createMavenLocalRepository() {
        MavenArtifactRepository mavenRepository = instantiator.newInstance(DefaultMavenLocalArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), fileResourceRepository, mavenMetadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService);
        File localMavenRepository = localMavenRepositoryLocator.getLocalMavenRepository();
        mavenRepository.setUrl(localMavenRepository);
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createJCenterRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(DefaultRepositoryHandler.BINTRAY_JCENTER_URL));
        mavenRepository.setUrl(DefaultRepositoryHandler.BINTRAY_JCENTER_URL);
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createMavenCentralRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(RepositoryHandler.MAVEN_CENTRAL_URL));
        mavenRepository.setUrl(RepositoryHandler.MAVEN_CENTRAL_URL);
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createGoogleRepository() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(RepositoryHandler.GOOGLE_URL));
        mavenRepository.setUrl(RepositoryHandler.GOOGLE_URL);
        return mavenRepository;
    }

    @Override
    public IvyArtifactRepository createIvyRepository() {
        return instantiator.newInstance(DefaultIvyArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, artifactFileStore, externalResourcesFileStore, createAuthenticationContainer(), ivyContextManager, moduleIdentifierFactory, instantiatorFactory, fileResourceRepository, metadataParser, ivyMetadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService, providerFactory);
    }

    @Override
    public MavenArtifactRepository createMavenRepository() {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), externalResourcesFileStore, fileResourceRepository, mavenMetadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService, providerFactory);
    }

    public MavenArtifactRepository createMavenRepository(Transformer<String, MavenArtifactRepository> describer) {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, describer, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), externalResourcesFileStore, fileResourceRepository, mavenMetadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService, providerFactory);
    }

    protected AuthenticationContainer createAuthenticationContainer() {
        DefaultAuthenticationContainer container = instantiator.newInstance(DefaultAuthenticationContainer.class, instantiator, callbackActionDecorator);

        for (Map.Entry<Class<Authentication>, Class<? extends Authentication>> e : authenticationSchemeRegistry.getRegisteredSchemes().entrySet()) {
            container.registerBinding(e.getKey(), e.getValue());
        }

        return container;
    }

    private static class NamedMavenRepositoryDescriber implements Transformer<String, MavenArtifactRepository> {
        private final String defaultUrl;

        private NamedMavenRepositoryDescriber(String defaultUrl) {
            this.defaultUrl = defaultUrl;
        }

        @Override
        public String transform(MavenArtifactRepository repository) {
            URI url = repository.getUrl();
            if (url == null || defaultUrl.equals(url.toString())) {
                return repository.getName();
            }
            return repository.getName() + '(' + url + ')';
        }
    }
}
