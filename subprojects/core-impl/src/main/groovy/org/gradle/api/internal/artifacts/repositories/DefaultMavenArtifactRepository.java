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

import com.google.common.collect.Lists;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.ModuleVersionPublisher;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.ConfiguredModuleVersionRepository;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.strategy.ResolverStrategy;
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData;
import org.gradle.api.internal.artifacts.repositories.resolver.MavenResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.api.internal.file.FileResolver;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DefaultMavenArtifactRepository extends AbstractAuthenticationSupportedRepository implements MavenArtifactRepository {
    private final FileResolver fileResolver;
    private final RepositoryTransportFactory transportFactory;
    private Object url;
    private List<Object> additionalUrls = new ArrayList<Object>();
    private final LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder;
    private final ResolverStrategy resolverStrategy;

    public DefaultMavenArtifactRepository(FileResolver fileResolver, PasswordCredentials credentials, RepositoryTransportFactory transportFactory,
                                          LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> locallyAvailableResourceFinder,
                                          ResolverStrategy resolverStrategy) {
        super(credentials);
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.resolverStrategy = resolverStrategy;
    }

    public URI getUrl() {
        return url == null ? null : fileResolver.resolveUri(url);
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

    public void setArtifactUrls(Iterable<?> urls) {
        additionalUrls = Lists.newArrayList(urls);
    }

    public ModuleVersionPublisher createPublisher() {
        return createRealResolver();
    }

    public DependencyResolver createLegacyDslObject() {
        MavenResolver resolver = createRealResolver();
        return new LegacyMavenResolver(resolver);
    }

    public ConfiguredModuleVersionRepository createResolver() {
        return createRealResolver();
    }

    protected MavenResolver createRealResolver() {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        MavenResolver resolver = new MavenResolver(getName(), rootUri, getTransport(rootUri.getScheme()),
                locallyAvailableResourceFinder, resolverStrategy);
        for (URI repoUrl : getArtifactUrls()) {
            resolver.addArtifactLocation(repoUrl, null);
        }
        return resolver;
    }

    protected RepositoryTransport getTransport(String scheme) {
        if (scheme.equalsIgnoreCase("file")) {
            return transportFactory.createFileTransport(getName());
        } else {
            return transportFactory.createHttpTransport(getName(), getCredentials());
        }
    }

    protected LocallyAvailableResourceFinder<ModuleVersionArtifactMetaData> getLocallyAvailableResourceFinder() {
        return locallyAvailableResourceFinder;
    }

    protected ResolverStrategy getResolverStrategy() {
        return resolverStrategy;
    }
}
