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

import org.gradle.api.Nullable;
import org.gradle.api.internal.filestore.url.DefaultUrlFileStore;
import org.gradle.internal.resolve.result.ResourceAwareResolveResult;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.local.LocallyAvailableResource;
import org.gradle.internal.resource.local.LocallyAvailableResourceCandidates;
import org.gradle.internal.resource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.resource.transfer.CacheAwareExternalResourceAccessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

public class DefaultExternalResourceUrlResolver implements ExternalResourceUrlResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceUrlResolver.class);

    private final DefaultUrlFileStore defaultUrlFileStore;
    private final CacheAwareExternalResourceAccessor resourceAccessor;
    private final LocallyAvailableResourceFinder<URL> locallyAvailableResourceFinder;

    public DefaultExternalResourceUrlResolver(DefaultUrlFileStore defaultUrlFileStore, CacheAwareExternalResourceAccessor resourceAccessor, LocallyAvailableResourceFinder<URL> locallyAvailableResourceFinder) {
        this.defaultUrlFileStore = defaultUrlFileStore;
        this.resourceAccessor = resourceAccessor;
        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
    }

    @Nullable
    @Override
    public LocallyAvailableExternalResource resolveUrl(URL url, ResourceAwareResolveResult result) {
        LOGGER.debug("Loading {}", url.toString());
        return downloadStaticResource(url, result);
    }

    @Override
    public boolean artifactExists(URL url, ResourceAwareResolveResult result) {
        result.attempted(url.toString());
        LOGGER.debug("Loading {}", url.toString());

        return !defaultUrlFileStore.search(url).isEmpty();
    }

    private LocallyAvailableExternalResource downloadStaticResource(final URL url, ResourceAwareResolveResult result) {
        result.attempted(url.toString());
        LOGGER.debug("Loading {}", url.toString());
        LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(url);

        URI uriLocation;
        try {
            uriLocation = url.toURI();
        } catch (URISyntaxException e) {
            return null;
        }

        try {
            ExternalResourceName resourceName = new ExternalResourceName(uriLocation);
            return resourceAccessor.getResource(resourceName, new CacheAwareExternalResourceAccessor.ResourceFileStore() {
                public LocallyAvailableResource moveIntoCache(File downloadedResource) {
                    return defaultUrlFileStore.move(url, downloadedResource);
                }
            }, localCandidates);
        } catch (Exception e) {
            throw ResourceExceptions.getFailed(uriLocation, e);
        }
    }
}
