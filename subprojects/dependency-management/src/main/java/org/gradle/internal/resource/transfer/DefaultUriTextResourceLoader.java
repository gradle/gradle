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

import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.dsl.dependencies.UriTextResourceLoader;
import org.gradle.api.internal.artifacts.repositories.resolver.ExternalResourceUrlResolver;
import org.gradle.internal.resolve.result.DefaultResourceAwareResolveResult;
import org.gradle.internal.resource.ProxyUriTextResource;
import org.gradle.internal.resource.TextResource;
import org.gradle.internal.resource.UriTextResource;
import org.gradle.internal.resource.local.LocallyAvailableExternalResource;

import java.net.MalformedURLException;
import java.net.URI;

public class DefaultUriTextResourceLoader implements UriTextResourceLoader {

    private final ExternalResourceUrlResolver externalResourceUrlResolver;

    public DefaultUriTextResourceLoader(ExternalResourceUrlResolver externalResourceUrlResolver) {
        this.externalResourceUrlResolver = externalResourceUrlResolver;
    }

    @Override
    public TextResource getResource(URI source) {
        DefaultResourceAwareResolveResult defaultResourceAwareResolveResult = new DefaultResourceAwareResolveResult();
        try {
            LocallyAvailableExternalResource resource = externalResourceUrlResolver.resolveUrl(source.toURL(), defaultResourceAwareResolveResult);
            if(resource == null) {
                throw new RuntimeException("Unable to find " + source.toString());
            }
            String encoding = extractContentType(resource);
            return new ProxyUriTextResource(new UriTextResource("cached-script", resource.getLocalResource().getFile(), source, encoding), source);
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String extractContentType(LocallyAvailableExternalResource resource) {
        if(resource.getMetaData() == null) {
            return UriTextResource.DEFAULT_ENCODING;
        }
        return UriTextResource.extractCharacterEncoding(resource.getMetaData().getContentType(), UriTextResource.DEFAULT_ENCODING);
    }
}
