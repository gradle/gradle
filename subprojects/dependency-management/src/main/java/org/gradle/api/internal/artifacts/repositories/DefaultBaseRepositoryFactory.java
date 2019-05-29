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

import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.api.internal.artifacts.BaseRepositoryFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.dsl.DefaultRepositoryHandler;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.authentication.Authentication;
import org.gradle.internal.authentication.AuthenticationSchemeRegistry;
import org.gradle.internal.authentication.DefaultAuthenticationContainer;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
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
    private final GradleModuleMetadataParser metadataParser;
    private final AuthenticationSchemeRegistry authenticationSchemeRegistry;
    private final IvyContextManager ivyContextManager;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final FileResourceRepository fileResourceRepository;
    private final FeaturePreviews featurePreviews;
    private final MavenMutableModuleMetadataFactory mavenMetadataFactory;
    private final IvyMutableModuleMetadataFactory ivyMetadataFactory;
    private final IsolatableFactory isolatableFactory;
    private final ObjectFactory objectFactory;
    private CollectionCallbackActionDecorator callbackActionDecorator;

    public DefaultBaseRepositoryFactory(LocalMavenRepositoryLocator localMavenRepositoryLocator,
                                        FileResolver fileResolver,
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
                                        FeaturePreviews featurePreviews,
                                        MavenMutableModuleMetadataFactory mavenMetadataFactory,
                                        IvyMutableModuleMetadataFactory ivyMetadataFactory,
                                        IsolatableFactory isolatableFactory,
                                        ObjectFactory objectFactory,
                                        CollectionCallbackActionDecorator callbackActionDecorator) {
        this.localMavenRepositoryLocator = localMavenRepositoryLocator;
        this.fileResolver = fileResolver;
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
        this.featurePreviews = featurePreviews;
        this.mavenMetadataFactory = mavenMetadataFactory;
        this.ivyMetadataFactory = ivyMetadataFactory;
        this.isolatableFactory = isolatableFactory;
        this.objectFactory = objectFactory;
        this.callbackActionDecorator = callbackActionDecorator;
    }

    @Override
    public FlatDirectoryArtifactRepository createFlatDirRepository() {
        return instantiator.newInstance(DefaultFlatDirArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, artifactFileStore, moduleIdentifierFactory, ivyMetadataFactory, instantiatorFactory, objectFactory);
    }

    @Override
    public ArtifactRepository createGradlePluginPortal() {
        MavenArtifactRepository mavenRepository = createMavenRepository(new NamedMavenRepositoryDescriber(PLUGIN_PORTAL_DEFAULT_URL));
        mavenRepository.setUrl(System.getProperty(PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY, PLUGIN_PORTAL_DEFAULT_URL));
        mavenRepository.metadataSources(new Action<MavenArtifactRepository.MetadataSources>() {
            @Override
            public void execute(MavenArtifactRepository.MetadataSources metadataSources) {
                metadataSources.mavenPom();
            }
        });
        return mavenRepository;
    }

    @Override
    public MavenArtifactRepository createMavenLocalRepository() {
        MavenArtifactRepository mavenRepository = instantiator.newInstance(DefaultMavenLocalArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), moduleIdentifierFactory, fileResourceRepository, featurePreviews, mavenMetadataFactory, isolatableFactory, objectFactory);
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
        return instantiator.newInstance(DefaultIvyArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, artifactFileStore, externalResourcesFileStore, createAuthenticationContainer(), ivyContextManager, moduleIdentifierFactory, instantiatorFactory, fileResourceRepository, metadataParser, featurePreviews, ivyMetadataFactory, isolatableFactory, objectFactory);
    }

    @Override
    public MavenArtifactRepository createMavenRepository() {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), moduleIdentifierFactory, externalResourcesFileStore, fileResourceRepository, featurePreviews, mavenMetadataFactory, isolatableFactory, objectFactory);
    }

    public MavenArtifactRepository createMavenRepository(Transformer<String, MavenArtifactRepository> describer) {
        return instantiator.newInstance(DefaultMavenArtifactRepository.class, describer, fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory, artifactFileStore, pomParser, metadataParser, createAuthenticationContainer(), moduleIdentifierFactory, externalResourcesFileStore, fileResourceRepository, featurePreviews, mavenMetadataFactory, isolatableFactory, objectFactory);
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
