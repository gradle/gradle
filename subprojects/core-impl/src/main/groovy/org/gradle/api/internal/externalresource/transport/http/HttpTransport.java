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
package org.gradle.api.internal.externalresource.transport.http;

import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.DefaultExternalResourceRepository;
import org.gradle.api.internal.artifacts.repositories.ExternalResourceRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;

import java.net.URI;

public class HttpTransport implements RepositoryTransport {
    private final String name;
    private final PasswordCredentials credentials;
    private final RepositoryCacheManager repositoryCacheManager;

    public HttpTransport(String name, PasswordCredentials credentials, RepositoryCacheManager repositoryCacheManager) {
        this.name = name;
        this.credentials = credentials;
        this.repositoryCacheManager = repositoryCacheManager;
    }

    public ExternalResourceRepository getRepository() {
        HttpClientHelper http = new HttpClientHelper(new DefaultHttpSettings(credentials));
        return new DefaultExternalResourceRepository(
                name, new HttpResourceAccessor(http), new HttpResourceUploader(http), new HttpResourceLister()
        );
    }

    public void configureCacheManager(AbstractResolver resolver) {
        resolver.setRepositoryCacheManager(repositoryCacheManager);
    }

    public String convertToPath(URI uri) {
        return normalisePath(uri.toString());
    }

    private String normalisePath(String path) {
        if (path.endsWith("/")) {
            return path;
        }
        return path + "/";
    }
}
