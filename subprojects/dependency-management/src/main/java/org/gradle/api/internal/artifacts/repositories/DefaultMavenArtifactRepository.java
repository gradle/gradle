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
import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Transformer;
import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.maven.MavenMetadataLoader;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultGradleModuleMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultMavenPomMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.MavenMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceArtifactResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.changedetection.state.isolation.IsolatableFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableMavenModuleResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.gradle.api.internal.FeaturePreviews.Feature.GRADLE_METADATA;

public class DefaultMavenArtifactRepository extends AbstractAuthenticationSupportedRepository implements MavenArtifactRepository, ResolutionAwareRepository, PublicationAwareRepository {
    private static final DefaultMavenPomMetadataSource.MavenMetadataValidator NO_OP_VALIDATION_SERVICES = new DefaultMavenPomMetadataSource.MavenMetadataValidator() {
        @Override
        public boolean isUsableModule(String repoName, MutableMavenModuleResolveMetadata metadata, ExternalResourceArtifactResolver artifactResolver) {
            return true;
        }
    };

    private final Transformer<String, MavenArtifactRepository> describer;
    private final FileResolver fileResolver;
    private final RepositoryTransportFactory transportFactory;
    private Object url;
    private List<Object> additionalUrls = new ArrayList<Object>();
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final MetaDataParser<MutableMavenModuleResolveMetadata> pomParser;
    private final ModuleMetadataParser metadataParser;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final FileStore<String> resourcesFileStore;
    private final FileResourceRepository fileResourceRepository;
    private final MavenMutableModuleMetadataFactory metadataFactory;
    private final IsolatableFactory isolatableFactory;
    private final MavenMetadataSources metadataSources = new MavenMetadataSources();
    private final InstantiatorFactory instantiatorFactory;

    public DefaultMavenArtifactRepository(FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                          LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                          InstantiatorFactory instantiatorFactory,
                                          FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                          MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                          ModuleMetadataParser metadataParser,
                                          AuthenticationContainer authenticationContainer,
                                          ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                          FileStore<String> resourcesFileStore,
                                          FileResourceRepository fileResourceRepository,
                                          FeaturePreviews featurePreviews,
                                          MavenMutableModuleMetadataFactory metadataFactory,
                                          IsolatableFactory isolatableFactory) {
        this(new DefaultDescriber(), fileResolver, transportFactory, locallyAvailableResourceFinder, instantiatorFactory,
            artifactFileStore, pomParser, metadataParser, authenticationContainer, moduleIdentifierFactory,
            resourcesFileStore, fileResourceRepository, featurePreviews, metadataFactory, isolatableFactory);
    }

    public DefaultMavenArtifactRepository(Transformer<String, MavenArtifactRepository> describer,
                                          FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                          LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                          InstantiatorFactory instantiatorFactory,
                                          FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                          MetaDataParser<MutableMavenModuleResolveMetadata> pomParser,
                                          ModuleMetadataParser metadataParser,
                                          AuthenticationContainer authenticationContainer,
                                          ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                          FileStore<String> resourcesFileStore,
                                          FileResourceRepository fileResourceRepository,
                                          FeaturePreviews featurePreviews,
                                          MavenMutableModuleMetadataFactory metadataFactory,
                                          IsolatableFactory isolatableFactory) {
        super(instantiatorFactory.decorate(), authenticationContainer);
        this.describer = describer;
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.pomParser = pomParser;
        this.metadataParser = metadataParser;
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.resourcesFileStore = resourcesFileStore;
        this.fileResourceRepository = fileResourceRepository;
        this.metadataFactory = metadataFactory;
        this.isolatableFactory = isolatableFactory;
        this.metadataSources.setDefaults(featurePreviews);
        this.instantiatorFactory = instantiatorFactory;
    }

    @Override
    public String getDisplayName() {
        return describer.transform(this);
    }

    public URI getUrl() {
        return url == null ? null : fileResolver.resolveUri(url);
    }

    public void setUrl(URI url) {
        this.url = url;
    }

    public void setUrl(Object url) {
        this.url = url;
    }

    public Set<URI> getArtifactUrls() {
        Set<URI> result = new LinkedHashSet<URI>();
        for (Object additionalUrl : additionalUrls) {
            result.add(fileResolver.resolveUri(additionalUrl));
        }
        return result;
    }

    public void artifactUrls(Object... urls) {
        additionalUrls.addAll(Lists.newArrayList(urls));
    }

    @Override
    public void setArtifactUrls(Set<URI> urls) {
        setArtifactUrls((Iterable<?>) urls);
    }

    public void setArtifactUrls(Iterable<?> urls) {
        additionalUrls = Lists.newArrayList(urls);
    }

    public ModuleVersionPublisher createPublisher() {
        return createRealResolver();
    }

    public ConfiguredModuleComponentRepository createResolver() {
        return createRealResolver();
    }

    protected MavenResolver createRealResolver() {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        MavenResolver resolver = createResolver(rootUri);

        for (URI repoUrl : getArtifactUrls()) {
            resolver.addArtifactLocation(repoUrl);
        }
        return resolver;
    }

    private MavenResolver createResolver(URI rootUri) {
        RepositoryTransport transport = getTransport(rootUri.getScheme());
        MavenMetadataLoader mavenMetadataLoader = new MavenMetadataLoader(transport.getResourceAccessor(), resourcesFileStore);
        ImmutableMetadataSources metadataSources = createMetadataSources(mavenMetadataLoader);
        Instantiator injector = createInjectorForMetadataSuppliers(transport, instantiatorFactory, getUrl(), resourcesFileStore);
        InstantiatingAction<ComponentMetadataSupplierDetails> supplier = createComponentMetadataSupplierFactory(injector, isolatableFactory);
        InstantiatingAction<ComponentMetadataListerDetails> lister = createComponentMetadataVersionLister(injector, isolatableFactory);
        return new MavenResolver(getName(), rootUri, transport, locallyAvailableResourceFinder, artifactFileStore, moduleIdentifierFactory, metadataSources, MavenMetadataArtifactProvider.INSTANCE, mavenMetadataLoader, supplier, lister, injector);
    }

    @Override
    public void metadataSources(Action<? super MetadataSources> configureAction) {
        metadataSources.reset();
        configureAction.execute(metadataSources);
    }

    ImmutableMetadataSources createMetadataSources(MavenMetadataLoader mavenMetadataLoader) {
        ImmutableList.Builder<MetadataSource<?>> sources = ImmutableList.builder();
        if (metadataSources.gradleMetadata) {
            // Don't list versions for gradleMetadata if maven-metadata.xml will be checked.
            boolean listVersionsForGradleMetadata = !metadataSources.mavenPom;
            sources.add(new DefaultGradleModuleMetadataSource(getMetadataParser(), metadataFactory, listVersionsForGradleMetadata));
        }
        if (metadataSources.mavenPom) {
            sources.add(new DefaultMavenPomMetadataSource(MavenMetadataArtifactProvider.INSTANCE, getPomParser(), fileResourceRepository, getMetadataValidationServices(), mavenMetadataLoader));
        }
        if (metadataSources.artifact) {
            sources.add(new DefaultArtifactMetadataSource(metadataFactory));
        }
        return new DefaultImmutableMetadataSources(sources.build());
    }

    protected DefaultMavenPomMetadataSource.MavenMetadataValidator getMetadataValidationServices() {
        return NO_OP_VALIDATION_SERVICES;
    }

    private MetaDataParser<MutableMavenModuleResolveMetadata> getPomParser() {
        return pomParser;
    }

    private ModuleMetadataParser getMetadataParser() {
        return metadataParser;
    }

    FileStore<ModuleComponentArtifactIdentifier> getArtifactFileStore() {
        return artifactFileStore;
    }

    FileStore<String> getResourcesFileStore() {
        return resourcesFileStore;
    }

    RepositoryTransport getTransport(String scheme) {
        return transportFactory.createTransport(scheme, getName(), getConfiguredAuthentication());
    }

    protected LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> getLocallyAvailableResourceFinder() {
        return locallyAvailableResourceFinder;
    }

    protected InstantiatorFactory getInstantiatorFactory() {
        return instantiatorFactory;
    }

    private static class DefaultDescriber implements Transformer<String, MavenArtifactRepository> {
        @Override
        public String transform(MavenArtifactRepository repository) {
            URI url = repository.getUrl();
            if (url == null) {
                return repository.getName();
            }
            return repository.getName() + '(' + url + ')';
        }
    }

    private static class MavenMetadataSources implements MetadataSources {
        boolean gradleMetadata;
        boolean mavenPom;
        boolean artifact;

        void setDefaults(FeaturePreviews featurePreviews) {
            mavenPom();
            if (featurePreviews.isFeatureEnabled(GRADLE_METADATA)) {
                gradleMetadata();
            } else {
                artifact();
            }
        }

        void reset() {
            gradleMetadata = false;
            mavenPom = false;
            artifact = false;
        }

        @Override
        public void gradleMetadata() {
            gradleMetadata = true;
        }

        @Override
        public void mavenPom() {
            mavenPom = true;
        }

        @Override
        public void artifact() {
            artifact = true;
        }
    }

}
