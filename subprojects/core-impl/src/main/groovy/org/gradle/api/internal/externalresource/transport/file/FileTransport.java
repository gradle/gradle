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
package org.gradle.api.internal.externalresource.transport.file;

import org.gradle.api.Nullable;
import org.gradle.api.internal.artifacts.repositories.cachemanager.RepositoryArtifactCache;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceResolver;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;
import org.gradle.api.internal.externalresource.ExternalResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.api.internal.externalresource.transport.DefaultExternalResourceRepository;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.file.TemporaryFileProvider;

import java.io.File;
import java.io.IOException;
import java.net.URI;

public class FileTransport implements RepositoryTransport {
    private final String name;
    private final RepositoryArtifactCache repositoryCacheManager;
    private final ExternalResourceRepository repository;

    public FileTransport(String name, RepositoryArtifactCache repositoryCacheManager, TemporaryFileProvider temporaryFileProvider) {
        this.name = name;
        this.repositoryCacheManager = repositoryCacheManager;
        repository = createRepository(temporaryFileProvider);
    }

    public ExternalResourceRepository getRepository() {
        return repository;
    }

    public ExternalResourceRepository createRepository(TemporaryFileProvider temporaryFileProvider) {
        FileResourceConnector connector = new FileResourceConnector();
        return new DefaultExternalResourceRepository(name, connector, connector, connector, temporaryFileProvider, new NoOpCacheAwareExternalResourceAccessor(connector));
    }

    public void configureCacheManager(ExternalResourceResolver resolver) {
        resolver.setRepositoryCacheManager(repositoryCacheManager);
    }

    public String convertToPath(URI uri) {
        return normalisePath(new File(uri).getAbsolutePath());
    }

    private String normalisePath(String path) {
        if (path.endsWith("/")) {
            return path;
        }
        return path + "/";
    }

    private static class NoOpCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {
        private final FileResourceConnector connector;

        public NoOpCacheAwareExternalResourceAccessor(FileResourceConnector connector) {
            this.connector = connector;
        }

        public ExternalResource getResource(String source, @Nullable LocallyAvailableResourceCandidates localCandidates) throws IOException {
            return connector.getResource(source);
        }
    }
}
