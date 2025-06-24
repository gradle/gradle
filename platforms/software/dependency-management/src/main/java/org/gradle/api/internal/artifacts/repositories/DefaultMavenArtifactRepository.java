/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.component.ModuleComponentIdentifier;
import org.gradle.api.artifacts.component.ModuleComponentSelector;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenRepositoryContentDescriptor;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ComponentResolvers;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.repositories.descriptor.MavenRepositoryDescriptor;
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultGradleModuleMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenPomMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.RedirectingGradleMetadataModuleMetadataSource;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.ResourcePattern;
import org.gradle.api.internal.artifacts.repositories.resolver.VersionLister;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.provider.SetProperty;
import org.gradle.internal.Cast;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.MutableModuleComponentResolveMetadata;
import org.gradle.internal.component.external.model.maven.MutableMavenModuleResolveMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resolve.result.BuildableModuleComponentMetaDataResolveResult;
import org.gradle.internal.resolve.result.BuildableModuleVersionListingResolveResult;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.jspecify.annotations.NonNull;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DefaultMavenArtifactRepository extends AbstractAuthenticationSupportedRepository<MavenRepositoryDescriptor> implements MavenArtifactRepository, ResolutionAwareRepository {
    private static final DefaultMavenPomMetadataSource.MavenMetadataValidator NO_OP_VALIDATION_SERVICES = (repoName, metadata, artifactResolver) -> true;

    private final Transformer<String, MavenArtifactRepository> describer;
    private final FileResolver fileResolver;
    private final RepositoryTransportFactory transportFactory;
    private final DefaultUrlArtifactRepository urlArtifactRepository;
    private final SetProperty<URI> additionalUrls;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser;
    private final GradleModuleMetadataParser metadataParser;
    private final FileStore<String> resourcesFileStore;
    private final FileResourceRepository fileResourceRepository;
    private final MavenMutableModuleMetadataFactory metadataFactory;
    private final IsolatableFactory isolatableFactory;
    private final ChecksumService checksumService;
    private final MavenMetadataSources metadataSources = new MavenMetadataSources();
    private final InstantiatorFactory instantiatorFactory;

    public DefaultMavenArtifactRepository(FileResolver fileResolver,
                                          RepositoryTransportFactory transportFactory,
                                          LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                          InstantiatorFactory instantiatorFactory,
                                          FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                          MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                          GradleModuleMetadataParser metadataParser,
                                          AuthenticationContainer authenticationContainer,
                                          FileStore<String> resourcesFileStore,
                                          FileResourceRepository fileResourceRepository,
                                          MavenMutableModuleMetadataFactory metadataFactory,
                                          IsolatableFactory isolatableFactory,
                                          ObjectFactory objectFactory,
                                          DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
                                          ChecksumService checksumService,
                                          ProviderFactory providerFactory,
                                          VersionParser versionParser
    ) {
        this(new DefaultDescriber(), fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory,
            artifactFileStore, pomParser, metadataParser, authenticationContainer,
            resourcesFileStore, fileResourceRepository, metadataFactory, isolatableFactory, objectFactory, urlArtifactRepositoryFactory, checksumService, providerFactory, versionParser);
    }

    public DefaultMavenArtifactRepository(Transformer<String, MavenArtifactRepository> describer,
                                          FileResolver fileResolver,
                                          RepositoryTransportFactory transportFactory,
                                          LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                          InstantiatorFactory instantiatorFactory,
                                          FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                          MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                          GradleModuleMetadataParser metadataParser,
                                          AuthenticationContainer authenticationContainer,
                                          FileStore<String> resourcesFileStore,
                                          FileResourceRepository fileResourceRepository,
                                          MavenMutableModuleMetadataFactory metadataFactory,
                                          IsolatableFactory isolatableFactory,
                                          ObjectFactory objectFactory,
                                          DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
                                          ChecksumService checksumService,
                                          ProviderFactory providerFactory,
                                          VersionParser versionParser
    ) {
        super(instantiatorFactory.decorateLenient(), authenticationContainer, objectFactory, providerFactory, versionParser);
        this.describer = describer;
        this.fileResolver = fileResolver;
        this.urlArtifactRepository = urlArtifactRepositoryFactory.create("Maven", this::getDisplayName);
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.pomParser = pomParser;
        this.metadataParser = metadataParser;
        this.resourcesFileStore = resourcesFileStore;
        this.fileResourceRepository = fileResourceRepository;
        this.metadataFactory = metadataFactory;
        this.isolatableFactory = isolatableFactory;
        this.checksumService = checksumService;
        this.metadataSources.setDefaults();
        this.instantiatorFactory = instantiatorFactory;
        this.additionalUrls = objectFactory.setProperty(URI.class);
    }

    @Override
    public String getDisplayName() {
        return describer.transform(this);
    }

    @Override
    public Property<URI> getUrl() {
        return urlArtifactRepository.getUrl();
    }

    @Override
    public void setUrl(Object url) {
        urlArtifactRepository.setUrl(url);
    }

    @Override
    public Property<Boolean> getAllowInsecureProtocol() {
        return urlArtifactRepository.getAllowInsecureProtocol();
    }

    @Override
    public SetProperty<URI> getArtifactUrls() {
        return additionalUrls;
    }

    @Override
    public void artifactUrls(Object... urls) {
        Arrays.stream(urls).map(fileResolver::resolveUri).forEach(additionalUrls::add);
    }

    @Override
    protected MavenRepositoryDescriptor createDescriptor() {
        URI rootUri = validateUrl();
        return new MavenRepositoryDescriptor.Builder(getName(), rootUri)
            .setAuthenticated(usesCredentials())
            .setAuthenticationSchemes(getAuthenticationSchemes())
            .setMetadataSources(metadataSources.asList())
            .setArtifactUrls(getArtifactUrls().get())
            .create();
    }

    @Override
    protected Collection<URI> getRepositoryUrls() {
        // In a similar way to Ivy, Maven may use other hosts for additional artifacts, but not POMs
        ImmutableList.Builder<URI> builder = ImmutableList.builder();
        URI root = getUrl().getOrNull();
        if (root != null) {
            builder.add(root);
        }
        builder.addAll(getArtifactUrls().get());
        return builder.build();
    }

    @NonNull
    protected URI validateUrl() {
        return urlArtifactRepository.validateUrl();
    }

    @Override
    public ConfiguredModuleComponentRepository createResolver() {
        URI rootUrl = validateUrl();
        return createResolver(rootUrl);
    }

    private MavenResolver createResolver(URI rootUri) {
        String scheme = rootUri.getScheme();
        if (scheme == null){
            throw new InvalidUserDataException("Repository URL must have a scheme: '" + rootUri + "'. If you are using a local repository, please use 'file()' or derive it from project.layout.");
        }
        RepositoryTransport transport = getTransport(scheme);
        MavenMetadataLoader mavenMetadataLoader = new MavenMetadataLoader(transport.getResourceAccessor(), resourcesFileStore);
        ImmutableMetadataSources metadataSources = createMetadataSources(mavenMetadataLoader);
        Instantiator injector = createInjectorForMetadataSuppliers(transport, instantiatorFactory, getUrl().get(), resourcesFileStore);
        InstantiatingAction<ComponentMetadataSupplierDetails> supplier = createComponentMetadataSupplierFactory(injector, isolatableFactory);
        InstantiatingAction<ComponentMetadataListerDetails> lister = createComponentMetadataVersionLister(injector, isolatableFactory);
        return new MavenResolver(getDescriptor(), rootUri, transport, locallyAvailableResourceFinder, artifactFileStore, metadataSources, MavenMetadataArtifactProvider.INSTANCE, mavenMetadataLoader, supplier, lister, injector, checksumService);
    }

    @Override
    public void metadataSources(Action<? super MetadataSources> configureAction) {
        metadataSources.reset();
        configureAction.execute(metadataSources);
    }

    @Override
    public MetadataSources getMetadataSources() {
        return metadataSources;
    }

    @Override
    public void mavenContent(Action<? super MavenRepositoryContentDescriptor> configureAction) {
        content(Cast.uncheckedCast(configureAction));
    }

    ImmutableMetadataSources createMetadataSources(MavenMetadataLoader mavenMetadataLoader) {
        ImmutableList.Builder<MetadataSource<?>> sources = ImmutableList.builder();
        // Don't list versions for gradleMetadata if maven-metadata.xml will be checked.
        boolean listVersionsForGradleMetadata = !metadataSources.mavenPom;
        MetadataSource<MutableModuleComponentResolveMetadata> gradleModuleMetadataSource =
            new MavenSnapshotDecoratingSource(
                new DefaultGradleModuleMetadataSource(getMetadataParser(), metadataFactory, listVersionsForGradleMetadata, checksumService)
            );
        if (metadataSources.gradleMetadata) {
            sources.add(gradleModuleMetadataSource);
        }
        if (metadataSources.mavenPom) {
            DefaultMavenPomMetadataSource pomMetadataSource = createPomMetadataSource(mavenMetadataLoader, fileResourceRepository);
            if (metadataSources.ignoreGradleMetadataRedirection) {
                sources.add(pomMetadataSource);
            } else {
                sources.add(new RedirectingGradleMetadataModuleMetadataSource(pomMetadataSource, gradleModuleMetadataSource));
            }
        }
        if (metadataSources.artifact) {
            sources.add(new DefaultArtifactMetadataSource(metadataFactory));
        }
        return new DefaultImmutableMetadataSources(sources.build());
    }

    protected DefaultMavenPomMetadataSource createPomMetadataSource(MavenMetadataLoader mavenMetadataLoader, FileResourceRepository fileResourceRepository) {
        return new DefaultMavenPomMetadataSource(MavenMetadataArtifactProvider.INSTANCE, getPomParser(), fileResourceRepository, getMetadataValidationServices(), mavenMetadataLoader, checksumService);
    }

    protected DefaultMavenPomMetadataSource.MavenMetadataValidator getMetadataValidationServices() {
        return NO_OP_VALIDATION_SERVICES;
    }

    MetaDataParser<MutableMavenModuleResolveMetadata> getPomParser() {
        return pomParser;
    }

    private GradleModuleMetadataParser getMetadataParser() {
        return metadataParser;
    }

    FileStore<ModuleComponentArtifactIdentifier> getArtifactFileStore() {
        return artifactFileStore;
    }

    FileStore<String> getResourcesFileStore() {
        return resourcesFileStore;
    }

    public RepositoryTransport getTransport(String scheme) {
        return transportFactory.createTransport(scheme, getName(), getConfiguredAuthentication(), urlArtifactRepository.createRedirectVerifier());
    }

    protected LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> getLocallyAvailableResourceFinder() {
        return locallyAvailableResourceFinder;
    }

    protected InstantiatorFactory getInstantiatorFactory() {
        return instantiatorFactory;
    }

    @Override
    public RepositoryContentDescriptorInternal createRepositoryDescriptor(VersionParser versionParser) {
        return new DefaultMavenRepositoryContentDescriptor(this::getDisplayName, versionParser);
    }

    private static class DefaultDescriber implements Transformer<String, MavenArtifactRepository> {
        @Override
        public String transform(MavenArtifactRepository repository) {
            URI url = repository.getUrl().getOrNull();
            if (url == null) {
                return repository.getName();
            }
            return repository.getName() + '(' + url + ')';
        }
    }

    private class MavenMetadataSources implements MetadataSources {
        boolean gradleMetadata;
        boolean mavenPom;
        boolean artifact;
        boolean ignoreGradleMetadataRedirection;

        void setDefaults() {
            mavenPom();
            ignoreGradleMetadataRedirection = false;
        }

        void reset() {
            gradleMetadata = false;
            mavenPom = false;
            artifact = false;
            ignoreGradleMetadataRedirection = false;
        }

        /**
         * This is used to generate the repository id and for reporting purposes on a Build Scan.
         * Changing this means a change of repository.
         *
         * @return a list of implemented metadata sources, as strings.
         */
        List<String> asList() {
            List<String> list = new ArrayList<>();
            if (gradleMetadata) {
                list.add("gradleMetadata");
            }
            if (mavenPom) {
                list.add("mavenPom");
            }
            if (artifact) {
                list.add("artifact");
            }
            if (ignoreGradleMetadataRedirection) {
                list.add("ignoreGradleMetadataRedirection");
            }
            return list;
        }

        @Override
        public void gradleMetadata() {
            invalidateDescriptor();
            gradleMetadata = true;
        }

        @Override
        public void mavenPom() {
            invalidateDescriptor();
            mavenPom = true;
        }

        @Override
        public void artifact() {
            invalidateDescriptor();
            artifact = true;
        }

        @Override
        public void ignoreGradleMetadataRedirection() {
            invalidateDescriptor();
            ignoreGradleMetadataRedirection = true;
        }

        @Override
        public boolean isGradleMetadataEnabled() {
            return gradleMetadata;
        }

        @Override
        public boolean isMavenPomEnabled() {
            return mavenPom;
        }

        @Override
        public boolean isArtifactEnabled() {
            return artifact;
        }

        @Override
        public boolean isIgnoreGradleMetadataRedirectionEnabled() {
            return ignoreGradleMetadataRedirection;
        }
    }

    private static class MavenSnapshotDecoratingSource implements MetadataSource<MutableModuleComponentResolveMetadata> {
        private final MetadataSource<MutableModuleComponentResolveMetadata> delegate;

        private MavenSnapshotDecoratingSource(MetadataSource<MutableModuleComponentResolveMetadata> delegate) {
            this.delegate = delegate;
        }

        @Override
        public MutableModuleComponentResolveMetadata create(String repositoryName, ComponentResolvers componentResolvers, ModuleComponentIdentifier moduleComponentIdentifier, ComponentOverrideMetadata prescribedMetaData, ExternalResourceArtifactResolver artifactResolver, BuildableModuleComponentMetaDataResolveResult<ModuleComponentResolveMetadata> result) {
            MutableModuleComponentResolveMetadata metadata = delegate.create(repositoryName, componentResolvers, moduleComponentIdentifier, prescribedMetaData, artifactResolver, result);
            if (metadata != null) {
                return MavenResolver.processMetaData((MutableMavenModuleResolveMetadata) metadata);
            }
            return null;
        }

        @Override
        public void listModuleVersions(ModuleComponentSelector selector, ComponentOverrideMetadata overrideMetadata, List<ResourcePattern> ivyPatterns, List<ResourcePattern> artifactPatterns, VersionLister versionLister, BuildableModuleVersionListingResolveResult result) {
            delegate.listModuleVersions(selector, overrideMetadata, ivyPatterns, artifactPatterns, versionLister, result);
        }
    }
}
