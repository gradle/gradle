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

import com.google.common.base.Objects;
import org.gradle.api.Action;
import org.gradle.api.artifacts.repositories.RepositoryResourceAccessor;
import org.gradle.internal.resolve.caching.ImplicitInputRecord;
import org.gradle.internal.resolve.caching.ImplicitInputsProvidingService;
import org.gradle.internal.resolve.caching.ImplicitInputRecorder;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;

import javax.annotation.Nullable;
import java.io.InputStream;
import java.net.URI;

public class ExternalRepositoryResourceAccessor implements RepositoryResourceAccessor, ImplicitInputsProvidingService<String, Long, RepositoryResourceAccessor> {
    private static final String SERVICE_TYPE = RepositoryResourceAccessor.class.getName();

    private final URI rootUri;
    private final String rootUriAsString;
    private final ExternalResourceAccessor resourceResolver;

    public ExternalRepositoryResourceAccessor(URI rootUri, CacheAwareExternalResourceAccessor cacheAwareExternalResourceAccessor, FileStore<String> fileStore) {
        this.rootUri = rootUri;
        this.rootUriAsString = rootUri.toString();
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

    @Override
    public RepositoryResourceAccessor withImplicitInputRecorder(final ImplicitInputRecorder registrar) {
        return (relativePath, action) -> {
            ExternalResourceName location = new ExternalResourceName(rootUri, relativePath);
            LocallyAvailableExternalResource resource = resourceResolver.resolveResource(location);
            registrar.register(SERVICE_TYPE, new ServiceCall(rootUriAsString + ";" + relativePath, hashFor(resource)));
            if (resource != null) {
                resource.withContent(action);
            }
        };
    }

    @Nullable
    private static Long hashFor(@Nullable LocallyAvailableExternalResource resource) {
        return resource == null ? null : resource.getMetaData().getLastModified().getTime();
    }

    @Override
    public boolean isUpToDate(String resource, @Nullable Long oldValue) {
        String[] parts = resource.split(";");
        if (!rootUriAsString.equals(parts[0])) {
            // not the same provider
            return false;
        }
        ExternalResourceName externalResourceName = new ExternalResourceName(rootUri, parts[1]);
        LocallyAvailableExternalResource locallyAvailableExternalResource = resourceResolver.resolveResource(externalResourceName);
        return Objects.equal(oldValue, hashFor(locallyAvailableExternalResource));
    }

    private static class ServiceCall implements ImplicitInputRecord<String, Long> {
        private final String resource;
        private final Long hash;

        private ServiceCall(String resource, @Nullable Long resourceHash) {
            this.resource = resource;
            this.hash = resourceHash;
        }

        @Override
        public String getInput() {
            return resource;
        }

        @Override
        public Long getOutput() {
            return hash;
        }
    }
}
