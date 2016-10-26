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

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.AuthenticationContainer;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.artifacts.repositories.IvyArtifactRepositoryMetaDataProvider;
import org.gradle.api.artifacts.repositories.RepositoryLayout;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.IvyContextManager;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleComponentRepository;
import org.gradle.api.internal.artifacts.repositories.layout.AbstractRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.DefaultIvyPatternRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.GradleRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.IvyRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.MavenRepositoryLayout;
import org.gradle.api.internal.artifacts.repositories.layout.ResolvedPattern;
import org.gradle.api.internal.artifacts.repositories.resolver.IvyResolver;
import org.gradle.api.internal.artifacts.repositories.resolver.PatternBasedResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.component.external.model.ModuleComponentArtifactIdentifier;
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.util.ConfigureUtil;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

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
    private final IvyContextManager ivyContextManager;

    public DefaultIvyArtifactRepository(FileResolver fileResolver, RepositoryTransportFactory transportFactory,
                                        LocallyAvailableResourceFinder<ModuleComponentArtifactMetadata> locallyAvailableResourceFinder, Instantiator instantiator,
                                        FileStore<ModuleComponentArtifactIdentifier> artifactFileStore, AuthenticationContainer authenticationContainer,
                                        IvyContextManager ivyContextManager) {
        super(instantiator, authenticationContainer);
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.artifactFileStore = artifactFileStore;
        this.additionalPatternsLayout = new AdditionalPatternsRepositoryLayout(fileResolver);
        this.layout = new GradleRepositoryLayout();
        this.metaDataProvider = new MetaDataProvider();
        this.instantiator = instantiator;
        this.ivyContextManager = ivyContextManager;
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
        return new IvyResolver(
                getName(), transport,
                locallyAvailableResourceFinder,
                metaDataProvider.dynamicResolve, artifactFileStore, ivyContextManager);
    }

    public URI getUrl() {
        return baseUrl == null ? null : fileResolver.resolveUri(baseUrl);
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
}
