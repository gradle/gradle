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

import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.local.FileStore;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor.DefaultResourceFileStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.net.URI;

public class DefaultExternalResourceAccessor implements ExternalResourceAccessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceAccessor.class);

    private final FileStore<String> fileStore;
    private final CacheAwareExternalResourceAccessor resourceAccessor;

    public DefaultExternalResourceAccessor(FileStore<String> fileStore, CacheAwareExternalResourceAccessor resourceAccessor) {
        this.fileStore = fileStore;
        this.resourceAccessor = resourceAccessor;
    }

    @Nullable
    @Override
    public LocallyAvailableExternalResource resolveUri(URI uri) {
        return resolve(new ExternalResourceName(uri));
    }

    @Nullable
    @Override
    public LocallyAvailableExternalResource resolveResource(ExternalResourceName resource) {
        return resolve(resource);
    }

    @Nullable
    private LocallyAvailableExternalResource resolve(final ExternalResourceName resource) {
        LOGGER.debug("Loading {}", resource);

        try {
            return resourceAccessor.getResource(resource, null, new DefaultResourceFileStore<String>(fileStore) {
                @Override
                protected String computeKey() {
                    return resource.toString();
                }
            }, null);
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(resource.getUri(), e);
        }
    }
}
