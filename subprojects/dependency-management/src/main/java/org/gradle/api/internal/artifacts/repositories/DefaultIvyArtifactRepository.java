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
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepositoryMetaDataProvider;
import org.gradle.api.artifacts.repositories.RepositoryLayout;
import org.gradle.api.internal.FeaturePreviews;
import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualMetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.ModuleMetadataParser;
import org.gradle.api.internal.artifacts.repositories.layout.AbstractRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.DefaultIvyPatternRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.GradleRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.IvyRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.MavenRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.ResolvedPattern;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultArtifactMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultGradleModuleMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.DefaultIvyDescriptorMetadataSource;
import org.gradle.api.internal.artifacts.repositories.metadata.ImmutableMetadataSources;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMetadataArtifactProvider;
import org.gradle.api.internal.artifacts.repositories.metadata.IvyMutableModuleMetadataFactory;
import org.gradle.api.internal.artifacts.repositories.metadata.MetadataSource;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.PatternBasedResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.changedetection.state.isolation.IsolatableFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.MutableIvyModuleResolveMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.util.ConfigureUtil;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.gradle.api.internal.FeaturePreviews.Feature.GRADLE_METADATA;

public class DefaultIvyArtifactRepository extends AbstractAuthenticationSupportedRepository implements IvyArtifactRepository, ResolutionAwareRepository, PublicationAwareRepository {
    private Object baseUrl;
    private AbstractRepositoryLayout layout;
    private final AdditionalPatternsRepositoryLayout additionalPatternsLayout;
    private final FileResolver fileResolver;
    private final RepositoryTransportFactory transportFactory;
    private final LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder;
    private final MetaDataProvider metaDataProvider;
    private final Instantiator instantiator;
    private final FileStore<ModuleComponentArtifactIdentifier> artifactFileStore;
    private final FileStore<String> externalResourcesFileStore;
    private final IvyContextManager ivyContextManager;
    private final ImmutableModuleIdentifierFactory moduleIdentifierFactory;
    private final InstantiatorFactory instantiatorFactory;
    private final FileResourceRepository fileResourceRepository;
    private final ModuleMetadataParser moduleMetadataParser;
    private final IvyMutableModuleMetadataFactory metadataFactory;
    private final IsolatableFactory isolatableFactory;
    private final IvyMetadataSources metadataSources = new IvyMetadataSources();

    public DefaultIvyArtifactRepository(FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
                                        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
                                        FileStore<String> externalResourcesFileStore,
                                        AuthenticationContainer authenticationContainer,
                                        IvyContextManager ivyContextManager,
                                        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
                                        InstantiatorFactory instantiatorFactory,
                                        FileResourceRepository fileResourceRepository,
                                        ModuleMetadataParser moduleMetadataParser,
                                        FeaturePreviews featurePreviews,
                                        IvyMutableModuleMetadataFactory metadataFactory,
                                        IsolatableFactory isolatableFactory) {
        super(instantiatorFactory.decorate(), authenticationContainer);
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.externalResourcesFileStore = externalResourcesFileStore;
        this.additionalPatternsLayout = new AdditionalPatternsRepositoryLayout(fileResolver);
        this.moduleIdentifierFactory = moduleIdentifierFactory;
        this.instantiatorFactory = instantiatorFactory;
        this.fileResourceRepository = fileResourceRepository;
        this.moduleMetadataParser = moduleMetadataParser;
        this.metadataFactory = metadataFactory;
        this.isolatableFactory = isolatableFactory;
        this.layout = new GradleRepositoryLayout();
        this.metaDataProvider = new MetaDataProvider();
        this.instantiator = instantiatorFactory.decorate();
        this.ivyContextManager = ivyContextManager;
        this.metadataSources.setDefaults(featurePreviews);
    }

    @Override
    public String getDisplayName() {
        URI url = getUrl();
        if (url == null) {
            return super.getDisplayName();
        }
        return super.getDisplayName() + '(' + url + ')';
    }

    public ModuleVersionPublisher createPublisher() {
        return createRealResolver();
    }

    public ConfiguredModuleComponentRepository createResolver() {
        return createRealResolver();
    }

    protected IvyResolver createRealResolver() {
        URI uri = getUrl();

        Set<String> schemes = new LinkedHashSet<String>();
        layout.addSchemes(uri, schemes);
        additionalPatternsLayout.addSchemes(uri, schemes);

        IvyResolver resolver = createResolver(schemes);

        layout.apply(uri, resolver);
        additionalPatternsLayout.apply(uri, resolver);

        return resolver;
    }

    private IvyResolver createResolver(Set<String> schemes) {
        if (schemes.isEmpty()) {
            throw new InvalidUserDataException("You must specify a base url or at least one artifact pattern for an Ivy repository.");
        }
        return createResolver(transportFactory.createTransport(schemes, getName(), getConfiguredAuthentication()));
    }

    private IvyResolver createResolver(RepositoryTransport transport) {
        Instantiator injector = createInjectorForMetadataSuppliers(transport, instantiatorFactory, getUrl(), externalResourcesFileStore);
        InstantiatingAction<ComponentMetadataSupplierDetails> supplierFactory = createComponentMetadataSupplierFactory(injector, isolatableFactory);
        InstantiatingAction<ComponentMetadataListerDetails> listerFactory = createComponentMetadataVersionLister(injector, isolatableFactory);
        return new IvyResolver(getName(), transport, locallyAvailableResourceFinder, metaDataProvider.dynamicResolve, artifactFileStore, moduleIdentifierFactory, supplierFactory, listerFactory, createMetadataSources(), IvyMetadataArtifactProvider.INSTANCE, injector);
    }

    @Override
    public void metadataSources(Action<? super MetadataSources> configureAction) {
        metadataSources.reset();
        configureAction.execute(metadataSources);
    }

    private ImmutableMetadataSources createMetadataSources() {
        ImmutableList.Builder<MetadataSource<?>> sources = ImmutableList.builder();
        if (metadataSources.gradleMetadata) {
            sources.add(new DefaultGradleModuleMetadataSource(moduleMetadataParser, metadataFactory, true));
        }
        if (metadataSources.ivyDescriptor) {
            sources.add(new DefaultIvyDescriptorMetadataSource(IvyMetadataArtifactProvider.INSTANCE, createIvyDescriptorParser(), fileResourceRepository, moduleIdentifierFactory));
        }
        if (metadataSources.artifact) {
            sources.add(new DefaultArtifactMetadataSource(metadataFactory));
        }
        return new DefaultImmutableMetadataSources(sources.build());
    }

    private MetaDataParser<MutableIvyModuleResolveMetadata> createIvyDescriptorParser() {
        return new IvyContextualMetaDataParser<MutableIvyModuleResolveMetadata>(ivyContextManager, new IvyXmlModuleDescriptorParser(new IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileResourceRepository, metadataFactory));
    }

    public URI getUrl() {
        return baseUrl == null ? null : fileResolver.resolveUri(baseUrl);
    }

    @Override
    public void setUrl(URI url) {
        baseUrl = url;
    }

    public void setUrl(Object url) {
        baseUrl = url;
    }

    public void artifactPattern(String pattern) {
        additionalPatternsLayout.artifactPatterns.add(pattern);
    }

    public void ivyPattern(String pattern) {
        additionalPatternsLayout.ivyPatterns.add(pattern);
    }

    public void layout(String layoutName) {
        if ("ivy".equals(layoutName)) {
            layout = instantiator.newInstance(IvyRepositoryLayout.class);
        } else if ("maven".equals(layoutName)) {
            layout = instantiator.newInstance(MavenRepositoryLayout.class);
        } else if ("pattern".equals(layoutName)) {
            layout = instantiator.newInstance(DefaultIvyPatternRepositoryLayout.class);
        } else {
            layout = instantiator.newInstance(GradleRepositoryLayout.class);
        }
    }

    public void layout(String layoutName, Closure config) {
        layout(layoutName, ConfigureUtil.<RepositoryLayout>configureUsing(config));
    }

    public void layout(String layoutName, Action<? extends RepositoryLayout> config) {
        layout(layoutName);
        ((Action) config).execute(layout);
    }

    public IvyArtifactRepositoryMetaDataProvider getResolve() {
        return metaDataProvider;
    }

    /**
     * Layout for applying additional patterns added via {@link #artifactPatterns} and {@link #ivyPatterns}.
     */
    private static class AdditionalPatternsRepositoryLayout extends AbstractRepositoryLayout {
        private final FileResolver fileResolver;
        private final Set<String> artifactPatterns = new LinkedHashSet<String>();
        private final Set<String> ivyPatterns = new LinkedHashSet<String>();

        public AdditionalPatternsRepositoryLayout(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        public void apply(URI baseUri, PatternBasedResolver resolver) {
            for (String artifactPattern : artifactPatterns) {
                ResolvedPattern resolvedPattern = new ResolvedPattern(artifactPattern, fileResolver);
                resolver.addArtifactLocation(resolvedPattern.baseUri, resolvedPattern.pattern);
            }

            Set<String> usedIvyPatterns = ivyPatterns.isEmpty() ? artifactPatterns : ivyPatterns;
            for (String ivyPattern : usedIvyPatterns) {
                ResolvedPattern resolvedPattern = new ResolvedPattern(ivyPattern, fileResolver);
                resolver.addDescriptorLocation(resolvedPattern.baseUri, resolvedPattern.pattern);
            }
        }

        @Override
        public void addSchemes(URI baseUri, Set<String> schemes) {
            for (String pattern : artifactPatterns) {
                schemes.add(new ResolvedPattern(pattern, fileResolver).scheme);
            }
            for (String pattern : ivyPatterns) {
                schemes.add(new ResolvedPattern(pattern, fileResolver).scheme);
            }
        }
    }

    private static class MetaDataProvider implements IvyArtifactRepositoryMetaDataProvider {
        boolean dynamicResolve;

        public boolean isDynamicMode() {
            return dynamicResolve;
        }

        public void setDynamicMode(boolean mode) {
            this.dynamicResolve = mode;
        }
    }

    private static class IvyMetadataSources implements MetadataSources {
        boolean gradleMetadata;
        boolean ivyDescriptor;
        boolean artifact;

        void setDefaults(FeaturePreviews featurePreviews) {
            ivyDescriptor();
            if (featurePreviews.isFeatureEnabled(GRADLE_METADATA)) {
                gradleMetadata();
            } else {
                artifact();
            }
        }

        void reset() {
            gradleMetadata = false;
            ivyDescriptor = false;
            artifact = false;
        }

        @Override
        public void gradleMetadata() {
            gradleMetadata = true;
        }

        @Override
        public void ivyDescriptor() {
            ivyDescriptor = true;
        }

        @Override
        public void artifact() {
            artifact = true;
        }
    }

}
