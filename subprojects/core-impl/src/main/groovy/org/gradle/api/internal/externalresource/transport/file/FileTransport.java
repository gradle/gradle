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

import org.apache.ivy.core.cache.RepositoryCacheManager;
import org.apache.ivy.plugins.resolver.AbstractResolver;
import org.gradle.api.internal.externalresource.transport.DefaultExternalResourceRepository;
import org.gradle.api.internal.externalresource.transport.ExternalResourceRepository;
import org.gradle.api.internal.artifacts.repositories.transport.RepositoryTransport;

import java.io.File;
import java.net.URI;

public class FileTransport implements RepositoryTransport {
    private final String name;
    private final RepositoryCacheManager repositoryCacheManager;

    public FileTransport(String name, RepositoryCacheManager repositoryCacheManager) {
        this.name = name;
        this.repositoryCacheManager = repositoryCacheManager;
    }

    public ExternalResourceRepository getRepository() {
        FileResourceConnector connector = new FileResourceConnector();
        return new DefaultExternalResourceRepository(name, connector, connector, connector);
    }

    public void configureCacheManager(AbstractResolver resolver) {
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
}
