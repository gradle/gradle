/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.resolver;

import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;

import java.io.InputStream;
import java.net.URI;

public class ExternalRepositoryResourceAccessor implements RepositoryResourceAccessor {
    private final URI rootUri;
    private final ExternalResourceAccessor resourceResolver;

    public ExternalRepositoryResourceAccessor(URI rootUri, CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor, FileStore<String> fileStore) {
        this.rootUri = rootUri;
        this.resourceResolver = new DefaultExternalResourceAccessor(fileStore, cacheAwareExternalResourceAccessor);
    }

    @Override
    public void withResource(String relativePath, Action<? super InputStream> action) {
        ExternalResourceName location = new ExternalResourceName(rootUri, relativePath);
        LocallyAvailableExternalResource resource = resourceResolver.resolveResource(location);
        if (resource != null) {
            resource.withContent(action);
        }
    }
}
