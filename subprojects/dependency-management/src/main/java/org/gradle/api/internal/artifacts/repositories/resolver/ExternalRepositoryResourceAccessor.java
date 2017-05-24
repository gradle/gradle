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

import com.google.common.base.Charsets;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;

import java.io.File;
import java.io.InputStream;
import java.net.URI;

public class ExternalRepositoryResourceAccessor implements RepositoryResourceAccessor {
    private final URI rootUri;
    private final CacheAwareExternalResourceAccessor resourceAccessor;
    private final FileStore<String> fileStore;

    public ExternalRepositoryResourceAccessor(URI rootUri, CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor, final FileStore<String> searchableFileStore) {
        this.rootUri = rootUri;

        this.resourceAccessor = cacheAwareExternalResourceAccessor;
        this.fileStore = searchableFileStore;
    }

    @Override
    public void withResource(String relativePath, Action<? super InputStream> action) {
        try {
            ExternalResourceName location = new ExternalResourceName(rootUri, relativePath);
            Hasher hasher = Hashing.sha1().newHasher().putString(location.getUri().toASCIIString(), Charsets.UTF_8);
            final String key = hasher.hash().toString();
            LocallyAvailableExternalResource resource = resourceAccessor.getResource(location, new CacheAwareExternalResourceAccessor.ResourceFileStore() {
                @Override
                public LocallyAvailableResource moveIntoCache(File downloadedResource) {
                    return fileStore.move(key, downloadedResource);
                }
            }, null);
            if (resource != null) {
                resource.withContent(action);
            }
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

}
