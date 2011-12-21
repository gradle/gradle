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

import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransportFactory;
import org.gradle.api.internal.file.FileResolver;

import java.net.URI;
import java.util.*;

import static org.gradle.util.GUtil.toList;

public class DefaultMavenArtifactRepository extends AbstractAuthenticationSupportedRepository implements MavenArtifactRepository, ArtifactRepositoryInternal {
    private final FileResolver fileResolver;
    private final RepositoryTransportFactory transportFactory;
    private String name;
    private Object url;
    private List<Object> additionalUrls = new ArrayList<Object>();

    public DefaultMavenArtifactRepository(FileResolver fileResolver, PasswordCredentials credentials, RepositoryTransportFactory transportFactory) {
        super(credentials);
        this.fileResolver = fileResolver;
        this.transportFactory = transportFactory;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        additionalUrls.addAll(Arrays.asList(urls));
    }

    public void setArtifactUrls(Iterable<?> urls) {
        additionalUrls = toList(urls);
    }

    public DependencyResolver createResolver() {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        MavenResolver resolver = new MavenResolver(name, rootUri, getTransport(rootUri.getScheme()));
        for (URI repoUrl : getArtifactUrls()) {
            resolver.addArtifactLocation(repoUrl, null);
        }
        return resolver;
    }

    private RepositoryTransport getTransport(String scheme) {
        if (scheme.equalsIgnoreCase("file")) {
            return transportFactory.createFileTransport(name);
        } else {
            return transportFactory.createHttpTransport(name, getCredentials());
        }
    }

}
