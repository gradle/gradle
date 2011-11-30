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

import org.apache.ivy.plugins.repository.file.FileRepository;
import org.apache.ivy.plugins.resolver.DependencyResolver;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.ivyservice.ivyresolve.LocalFileRepositoryCacheManager;
import org.gradle.api.internal.artifacts.repositories.transport.DefaultHttpSettings;
import org.gradle.api.internal.artifacts.repositories.transport.HttpSettings;
import org.gradle.api.internal.file.FileResolver;

import java.io.File;
import java.net.URI;
import java.util.*;

import static org.gradle.util.GUtil.toList;

public class DefaultMavenArtifactRepository extends AbstractAuthenticationSupportedRepository implements MavenArtifactRepository, ArtifactRepositoryInternal {
    private final FileResolver fileResolver;
    private String name;
    private Object url;
    private List<Object> additionalUrls = new ArrayList<Object>();

    public DefaultMavenArtifactRepository(FileResolver fileResolver, PasswordCredentials credentials) {
        super(credentials);
        this.fileResolver = fileResolver;
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

    public void createResolvers(Collection<DependencyResolver> resolvers) {
        URI rootUri = getUrl();
        if (rootUri == null) {
            throw new InvalidUserDataException("You must specify a URL for a Maven repository.");
        }

        MavenResolver resolver = new MavenResolver();
        resolver.setName(name);

        if (rootUri.getScheme().equalsIgnoreCase("file")) {
            resolver.setRepository(new FileRepository());
            resolver.setRepositoryCacheManager(new LocalFileRepositoryCacheManager(name));

            resolver.setRoot(getFilePath(rootUri));
            
            Collection<URI> artifactUrls = getArtifactUrls();
            for (URI repoUrl : artifactUrls) {
                resolver.addArtifactUrl(getFilePath(repoUrl));
            }
        } else {
            HttpSettings httpSettings = new DefaultHttpSettings(getCredentials());
            resolver.setRepository(new CommonsHttpClientBackedRepository(httpSettings));
            resolver.setRoot(getUriPath(rootUri));

            Collection<URI> artifactUrls = getArtifactUrls();
            for (URI repoUrl : artifactUrls) {
                resolver.addArtifactUrl(getUriPath(repoUrl));
            }
        }
        resolvers.add(resolver);
    }
    
    // TODO:DAZ Need to work out a way to mixin the CommonsHttp vs LocalFile stuff into Ivy vs Maven resolvers
    private String getUriPath(URI uri) {
        return normalisePath(uri.toString());
    }
    
    private String getFilePath(URI fileUri) {
        return normalisePath(new File(fileUri).getAbsolutePath());
    }
    
    private String normalisePath(String path) {
        if (path.endsWith("/")) {
            return path;
        }
        return path + "/";
    }
}
