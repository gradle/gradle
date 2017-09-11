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

package org.gradle.internal.resource.transfer;

import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceAccessor;
import org.gradle.internal.resource.BasicTextResourceLoader;
import org.gradle.internal.resource.DownloadedUriTextResource;
import org.gradle.internal.resource.ResourceExceptions;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.net.URI;
import java.util.Set;

public class DefaultUriTextResourceLoader extends BasicTextResourceLoader {

    private final ExternalResourceAccessor externalResourceAccessor;
    private final Set<String> cachedSchemes;

    public DefaultUriTextResourceLoader(ExternalResourceAccessor externalResourceAccessor, Set<String> cachedSchemes) {
        this.externalResourceAccessor = externalResourceAccessor;
        this.cachedSchemes = cachedSchemes;
    }

    @Override
    public TextResource loadUri(String description, URI source) {
        if (isCacheable(source)) {
            LocallyAvailableExternalResource resource = externalResourceAccessor.resolveUri(source);
            if (resource == null) {
                throw ResourceExceptions.getMissing(source);
            }
            ExternalResourceMetaData metaData = resource.getMetaData();
            String contentType = metaData == null ? null : metaData.getContentType();
            return new DownloadedUriTextResource(description, source, contentType, resource.getFile());
        } else {
            // fallback to old behavior of always loading the resource
            return super.loadUri(description, source);
        }

    }

    private boolean isCacheable(URI source) {
        return isCacheableScheme(source) && isCacheableResource(source);
    }

    /**
     * Is {@code source} a cacheable transport scheme (e.g., http)?
     *
     * Schemes like file:// are not cacheable.
     */
    private boolean isCacheableScheme(URI source) {
        return cachedSchemes.contains(source.getScheme());
    }

    /**
     * Is {@code source} a cacheable URL?
     *
     * A URI is not cacheable if it uses a query string because our underlying infrastructure
     * relies on paths to uniquely identify resources and not path+query components.
     */
    private boolean isCacheableResource(URI source) {
        return source.getRawQuery() == null;
    }
}
