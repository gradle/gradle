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
import org.gradle.api.artifacts.ComponentMetadataListerDetails;
import org.gradle.api.artifacts.ComponentMetadataSupplierDetails;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepositoryMetaDataProvider;
import org.gradle.api.artifacts.repositories.IvyPatternRepositoryLayout;
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextualMetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.GradleModuleMetadataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyModuleDescriptorConverter;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.IvyXmlModuleDescriptorParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.parser.MetaDataParser;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.VersionParser;
import org.gradle.api.internal.artifacts.repositories.descriptor.IvyRepositoryDescriptor;
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
import org.gradle.api.internal.artifacts.repositories.metadata.RedirectingGradleMetadataModuleMetadataSource;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.internal.action.InstantiatingAction;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.component.external.model.ivy.MutableIvyModuleResolveMetadata;
import org.gradle.internal.hash.ChecksumService;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static java.util.Collections.unmodifiableSet;

public class DefaultIvyArtifactRepository extends AbstractAuthenticationSupportedRepository<IvyRepositoryDescriptor> implements IvyArtifactRepository, ResolutionAwareRepository {
    private volatile Set<String> schemes;
    private AbstractRepositoryLayout layout;
    private final DefaultUrlArtifactRepository urlArtifactRepository;
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
    private final GradleModuleMetadataParser moduleMetadataParser;
    private final IvyMutableModuleMetadataFactory metadataFactory;
    private final IsolatableFactory isolatableFactory;
    private final ChecksumService checksumService;
    private final IvyMetadataSources metadataSources = new IvyMetadataSources();

    public DefaultIvyArtifactRepository(
        FileResolver fileResolver,
        RepositoryTransportFactory transportFactory,
        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder,
        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore,
        FileStore<String> externalResourcesFileStore,
        AuthenticationContainer authenticationContainer,
        IvyContextManager ivyContextManager,
        ImmutableModuleIdentifierFactory moduleIdentifierFactory,
        InstantiatorFactory instantiatorFactory,
        FileResourceRepository fileResourceRepository,
        GradleModuleMetadataParser moduleMetadataParser,
        IvyMutableModuleMetadataFactory metadataFactory,
        IsolatableFactory isolatableFactory,
        ObjectFactory objectFactory,
        DefaultUrlArtifactRepository.Factory urlArtifactRepositoryFactory,
        ChecksumService checksumService,
        ProviderFactory providerFactory,
        VersionParser versionParser
    ) {
        super(instantiatorFactory.decorateLenient(), authenticationContainer, objectFactory, providerFactory, versionParser);
        this.fileResolver = fileResolver;
        this.urlArtifactRepository = urlArtifactRepositoryFactory.create("Ivy", this::getDisplayName);
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
        this.checksumService = checksumService;
        this.layout = new GradleRepositoryLayout();
        this.metaDataProvider = new MetaDataProvider();
        this.instantiator = instantiatorFactory.decorateLenient();
        this.ivyContextManager = ivyContextManager;
        this.metadataSources.setDefaults();
    }

    @Override
    public String getDisplayName() {
        URI url = getUrl();
        if (url == null) {
            return super.getDisplayName();
        }
        return super.getDisplayName() + '(' + url + ')';
    }

    public IvyResolver createPublisher() {
        return createRealResolver();
    }

    @Override
    public ConfiguredModuleComponentRepository createResolver() {
        return createRealResolver();
    }

    @Override
    protected IvyRepositoryDescriptor createDescriptor() {
        Set<String> schemes = getSchemes();
        validate(schemes);

        URI url = urlArtifactRepository.getUrl();
        IvyRepositoryDescriptor.Builder builder = new IvyRepositoryDescriptor.Builder(getName(), url)
            .setAuthenticated(usesCredentials())
            .setAuthenticationSchemes(getAuthenticationSchemes())
            .setMetadataSources(metadataSources.asList());
        layout.apply(url, builder);
        additionalPatternsLayout.apply(url, builder);
        return builder.create();
    }

    private IvyResolver createRealResolver() {
        Set<String> schemes = getSchemes();
        validate(schemes);
        return createResolver(schemes);
    }

    private IvyResolver createResolver(Set<String> schemes) {
        return createResolver(transportFactory.createTransport(schemes, getName(), getConfiguredAuthentication(), urlArtifactRepository.createRedirectVerifier()));
    }

    private void validate(Set<String> schemes) {
        if (schemes.isEmpty()) {
            throw new InvalidUserDataException("You must specify a base url or at least one artifact pattern for the Ivy repository '" + getDisplayName() + "'.");
        }
    }

    private Set<String> getSchemes() {
        if (schemes == null) {
            URI uri = getUrl();
            // use a local variable to prepare the set,
            // so that other threads do not see the half-initialized
            // list of schemes and fail in strange ways
            Set<String> result = new LinkedHashSet<>();
            layout.addSchemes(uri, result);
            additionalPatternsLayout.addSchemes(uri, result);
            schemes = unmodifiableSet(result);
        }
        return schemes;
    }

    private IvyResolver createResolver(RepositoryTransport transport) {
        Instantiator injector = createInjectorForMetadataSuppliers(transport, instantiatorFactory, getUrl(), externalResourcesFileStore);
        InstantiatingAction<ComponentMetadataSupplierDetails> supplierFactory = createComponentMetadataSupplierFactory(injector, isolatableFactory);
        InstantiatingAction<ComponentMetadataListerDetails> listerFactory = createComponentMetadataVersionLister(injector, isolatableFactory);
        return new IvyResolver(getDescriptor(), transport, locallyAvailableResourceFinder, metaDataProvider.dynamicResolve, artifactFileStore, supplierFactory, listerFactory, createMetadataSources(), IvyMetadataArtifactProvider.INSTANCE, injector, checksumService);
    }

    @Override
    public void metadataSources(Action<? super MetadataSources> configureAction) {
        invalidateDescriptor();
        metadataSources.reset();
        configureAction.execute(metadataSources);
    }

    @Override
    public MetadataSources getMetadataSources() {
        return metadataSources;
    }

    private ImmutableMetadataSources createMetadataSources() {
        ImmutableList.Builder<MetadataSource<?>> sources = ImmutableList.builder();
        DefaultGradleModuleMetadataSource gradleModuleMetadataSource = new DefaultGradleModuleMetadataSource(moduleMetadataParser, metadataFactory, true, checksumService);
        if (metadataSources.gradleMetadata) {
            sources.add(gradleModuleMetadataSource);
        }
        if (metadataSources.ivyDescriptor) {
            DefaultIvyDescriptorMetadataSource ivyDescriptorMetadataSource = new DefaultIvyDescriptorMetadataSource(IvyMetadataArtifactProvider.INSTANCE, createIvyDescriptorParser(), fileResourceRepository, checksumService);
            if (metadataSources.ignoreGradleMetadataRedirection) {
                sources.add(ivyDescriptorMetadataSource);
            } else {
                sources.add(new RedirectingGradleMetadataModuleMetadataSource(ivyDescriptorMetadataSource, gradleModuleMetadataSource));
            }
        }
        if (metadataSources.artifact) {
            sources.add(new DefaultArtifactMetadataSource(metadataFactory));
        }
        return new DefaultImmutableMetadataSources(sources.build());
    }

    private MetaDataParser<MutableIvyModuleResolveMetadata> createIvyDescriptorParser() {
        return new IvyContextualMetaDataParser<>(ivyContextManager, new IvyXmlModuleDescriptorParser(new IvyModuleDescriptorConverter(moduleIdentifierFactory), moduleIdentifierFactory, fileResourceRepository, metadataFactory));
    }

    @Override
    public URI getUrl() {
        return urlArtifactRepository.getUrl();
    }


    @Override
    protected Collection<URI> getRepositoryUrls() {
        // Ivy can resolve files from multiple hosts, so we need to look at all
        // of the possible URLs used by the Ivy resolver to identify all of the repositories
        ImmutableList.Builder<URI> builder = ImmutableList.builder();
        URI root = getUrl();
        if (root != null) {
            builder.add(root);
        }
        for (String pattern : additionalPatternsLayout.artifactPatterns) {
            URI baseUri = new ResolvedPattern(pattern, fileResolver).baseUri;
            if (baseUri != null) {
                builder.add(baseUri);
            }
        }
        for (String pattern : additionalPatternsLayout.ivyPatterns) {
            URI baseUri = new ResolvedPattern(pattern, fileResolver).baseUri;
            if (baseUri != null) {
                builder.add(baseUri);
            }
        }
        return builder.build();
    }

    @Override
    public void setUrl(URI url) {
        invalidateDescriptor();
        urlArtifactRepository.setUrl(url);
    }

    @Override
    public void setUrl(Object url) {
        invalidateDescriptor();
        urlArtifactRepository.setUrl(url);
    }

    @Override
    public void setAllowInsecureProtocol(boolean allowInsecureProtocol) {
        invalidateDescriptor();
        urlArtifactRepository.setAllowInsecureProtocol(allowInsecureProtocol);
    }

    @Override
    public boolean isAllowInsecureProtocol() {
        return urlArtifactRepository.isAllowInsecureProtocol();
    }

    @Override
    public void artifactPattern(String pattern) {
        invalidateDescriptor();
        additionalPatternsLayout.artifactPatterns.add(pattern);
    }

    @Override
    public void ivyPattern(String pattern) {
        invalidateDescriptor();
        additionalPatternsLayout.ivyPatterns.add(pattern);
    }

    public Set<String> additionalArtifactPatterns() {
        return additionalPatternsLayout.artifactPatterns;
    }

    public Set<String> additionalIvyPatterns() {
        return additionalPatternsLayout.ivyPatterns;
    }

    @Override
    public void layout(String layoutName) {
        invalidateDescriptor();
        switch (layoutName) {
            case "ivy":
                layout = instantiator.newInstance(IvyRepositoryLayout.class);
                break;
            case "maven":
                layout = instantiator.newInstance(MavenRepositoryLayout.class);
                break;
            case "pattern":
                layout = instantiator.newInstance(DefaultIvyPatternRepositoryLayout.class);
                break;
            default:
                layout = instantiator.newInstance(GradleRepositoryLayout.class);
                break;
        }
    }

    @Override
    public void patternLayout(Action<? super IvyPatternRepositoryLayout> config) {
        invalidateDescriptor();
        DefaultIvyPatternRepositoryLayout layout = instantiator.newInstance(DefaultIvyPatternRepositoryLayout.class);
        this.layout = layout;
        config.execute(layout);
    }

    public AbstractRepositoryLayout getRepositoryLayout() {
        return layout;
    }

    @Override
    public IvyArtifactRepositoryMetaDataProvider getResolve() {
        return metaDataProvider;
    }

    public void setRepositoryLayout(AbstractRepositoryLayout layout) {
        invalidateDescriptor();
        this.layout = layout;
    }

    @Override
    protected void invalidateDescriptor() {
        super.invalidateDescriptor();
        schemes = null;
    }

    public boolean hasStandardPattern() {
        // This is wasteful because we create a descriptor and throw it away immediately.
        IvyRepositoryDescriptor descriptor = createDescriptor();
        List<String> artifactPatterns = descriptor.getArtifactPatterns();
        if (artifactPatterns.size() == 1) {
            return artifactPatterns.get(0).equals(IvyArtifactRepository.GRADLE_ARTIFACT_PATTERN);
        } else {
            return false;
        }
    }

    /**
     * Layout for applying additional patterns added via {@link #artifactPatterns} and {@link #ivyPatterns}.
     */
    private static class AdditionalPatternsRepositoryLayout extends AbstractRepositoryLayout {
        private final FileResolver fileResolver;
        private final Set<String> artifactPatterns = new LinkedHashSet<>();
        private final Set<String> ivyPatterns = new LinkedHashSet<>();

        public AdditionalPatternsRepositoryLayout(FileResolver fileResolver) {
            this.fileResolver = fileResolver;
        }

        @Override
        public void apply(@Nullable URI baseUri, IvyRepositoryDescriptor.Builder builder) {
            for (String artifactPattern : artifactPatterns) {
                ResolvedPattern resolvedPattern = new ResolvedPattern(artifactPattern, fileResolver);
                builder.addArtifactPattern(artifactPattern);
                builder.addArtifactResource(resolvedPattern.baseUri, resolvedPattern.pattern);
            }

            for (String ivyPattern : ivyPatterns) {
                builder.addIvyPattern(ivyPattern);
            }
            Set<String> effectiveIvyPatterns = ivyPatterns.isEmpty() ? artifactPatterns : ivyPatterns;
            for (String ivyPattern : effectiveIvyPatterns) {
                ResolvedPattern resolvedPattern = new ResolvedPattern(ivyPattern, fileResolver);
                builder.addIvyResource(resolvedPattern.baseUri, resolvedPattern.pattern);
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

        @Override
        public boolean isDynamicMode() {
            return dynamicResolve;
        }

        @Override
        public void setDynamicMode(boolean mode) {
            this.dynamicResolve = mode;
        }
    }

    private class IvyMetadataSources implements MetadataSources {
        boolean gradleMetadata;
        boolean ivyDescriptor;
        boolean artifact;
        boolean ignoreGradleMetadataRedirection;

        void setDefaults() {
            ivyDescriptor();
            ignoreGradleMetadataRedirection = false;
        }

        void reset() {
            gradleMetadata = false;
            ivyDescriptor = false;
            artifact = false;
            ignoreGradleMetadataRedirection = false;
        }

        /**
         * This is used to generate the repository id and for reporting purposes on build scans.
         * Changing this means a change of repository.
         *
         * @return a list of implemented metadata sources, as strings.
         */
        List<String> asList() {
            List<String> list = new ArrayList<>();
            if (gradleMetadata) {
                list.add("gradleMetadata");
            }
            if (ivyDescriptor) {
                list.add("ivyDescriptor");
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
        public void ivyDescriptor() {
            invalidateDescriptor();
            ivyDescriptor = true;
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
        public boolean isIvyDescriptorEnabled() {
            return ivyDescriptor;
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

}
