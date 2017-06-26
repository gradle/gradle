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
package org.gradle.internal.resource.transport.file;

import org.gradle.api.Nullable;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ExternalResourceRepository;
import org.gradle.internal.resource.local.FileResourceRepository;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transport.AbstractRepositoryTransport;

import java.io.IOException;

public class FileTransport extends AbstractRepositoryTransport {
    private final FileResourceRepository repository;
    private final NoOpCacheAwareExternalResourceAccessor resourceAccessor;

    public FileTransport(String name, FileResourceRepository repository) {
        super(name);
        this.repository = repository;
        resourceAccessor = new NoOpCacheAwareExternalResourceAccessor();
    }

    public boolean isLocal() {
        return true;
    }

    public ExternalResourceRepository getRepository() {
        return repository;
    }

    public CacheAwareExternalResourceAccessor getResourceAccessor() {
        return resourceAccessor;
    }

    private class NoOpCacheAwareExternalResourceAccessor implements CacheAwareExternalResourceAccessor {
        public LocallyAvailableExternalResource getResource(ExternalResourceName source, ResourceFileStore fileStore, @Nullable LocallyAvailableResourceCandidates additionalCandidates) throws IOException {
            LocallyAvailableExternalResource resource = repository.resource(source);
            if (resource.getFile().exists()) {
                return resource;
            }
            return null;
        }
    }
}
