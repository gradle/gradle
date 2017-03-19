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

package org.gradle.caching.http;

import com.google.common.base.Strings;
import org.gradle.api.Action;
import org.gradle.api.Incubating;
import org.gradle.api.Nullable;
import org.gradle.caching.configuration.AbstractBuildCache;
import org.gradle.util.SingleMessageLogger;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Configuration object for the HTTP build cache.
 *
 * The build cache only supports BASIC authentication currently.
 *
 * <p>Cache entries are loaded via {@literal GET} and stored via {@literal PUT} requests.</p>
 * For a {@literal GET} request we expect a 200 or 404 response and for {@literal PUT} we expect any 2xx response.
 * Other responses are treated as recoverable or non-recoverable errors, depending on the status code.
 * E.g. we treat authentication failures (401 and 409) as non-recoverable while an internal server error (500) is recoverable.
 *
 * @since 3.5
 */
@Incubating
public class HttpBuildCache extends AbstractBuildCache {
    private static final String HTTP_URI_PROPERTY = "org.gradle.cache.tasks.http.uri";

    private final HttpBuildCacheCredentials credentials;
    private URI url;

    public HttpBuildCache() {
        // TODO: Get rid of this system property in favor of the DSL
        String defaultUrl = System.getProperty(HTTP_URI_PROPERTY);
        if (defaultUrl != null) {
            SingleMessageLogger.nagUserOfDiscontinuedProperty(HTTP_URI_PROPERTY, "Use the build cache DSL instead.");
        }
        this.credentials = new HttpBuildCacheCredentials();
        this.url = Strings.isNullOrEmpty(defaultUrl)
            ? null
            : URI.create(defaultUrl);
    }

    /**
     * Returns the URI to the cache.
     */
    @Nullable
    public URI getUrl() {
        return url;
    }

    /**
     * Sets the URL of the cache. The URL must end in a '/'.
     */
    public void setUrl(String url) {
        setUrl(URI.create(url));
    }

    /**
     * Sets the URL of the cache. The URL must end in a '/'.
     */
    public void setUrl(URL url) throws URISyntaxException {
        setUrl(url.toURI());
    }

    public void setUrl(URI url) {
        this.url = url;
    }

    /**
     * Returns the credentials used to access the HTTP cache backend.
     */
    public HttpBuildCacheCredentials getCredentials() {
        return credentials;
    }

    /**
     * Configures the credentials used to access the HTTP cache backend.
     */
    public void credentials(Action<? super HttpBuildCacheCredentials> configuration) {
        configuration.execute(credentials);
    }
}
