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
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;

import java.net.URI;
import java.util.Set;

public class DefaultUriTextResourceLoader extends BasicTextResourceLoader {

    private final ExternalResourceAccessor externalResourceAccessor;
    private final Set<String> cachedSchemas;

    public DefaultUriTextResourceLoader(ExternalResourceAccessor externalResourceAccessor, Set<String> cachedSchemas) {
        this.externalResourceAccessor = externalResourceAccessor;
        this.cachedSchemas = cachedSchemas;
    }

    @Override
    public TextResource loadUri(String description, URI source) {
        if (!cachedSchemas.contains(source.getScheme())) {
            return super.loadUri(description, source);
        }

        LocallyAvailableExternalResource resource = externalResourceAccessor.resolveUri(source);
        if (resource == null) {
            // Use an uncached resource, allowing the build to make another request (and fail due to missing resource)
            // TODO: We have enough information to create a missing text resource instance here
            return super.loadUri(description, source);
        }
        ExternalResourceMetaData metaData = resource.getMetaData();
        String contentType = metaData == null ? null : metaData.getContentType();
        return new DownloadedUriTextResource(description, source, contentType, resource.getFile());
    }
}
