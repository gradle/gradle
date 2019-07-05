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

import org.gradle.api.Action;
import org.gradle.caching.configuration.AbstractBuildCache;

import javax.annotation.Nullable;
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
public class HttpBuildCache extends AbstractBuildCache {
    private final HttpBuildCacheCredentials credentials;
    private URI url;
    private boolean allowUntrustedServer;

    public HttpBuildCache() {
        this.credentials = new HttpBuildCacheCredentials();
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

    /**
     * Sets the URL of the cache. The URL must end in a '/'.
     */
    public void setUrl(@Nullable URI url) {
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

    /**
     * Specifies whether it is acceptable to communicate with an HTTP build cache backend with an untrusted SSL certificate.
     * <p>
     * The SSL certificate for the HTTP build cache backend may be untrusted since it is internally provisioned or a self-signed certificate.
     * <p>
     * In such a scenario, you can either configure the build JVM environment to trust the certificate,
     * or set this property to {@code true} to disable verification of the server's identity.
     * <p>
     * Allowing communication with untrusted servers keeps data encrypted during transmission,
     * but makes it easier for a man-in-the-middle to impersonate the intended server and capture data.
     * <p>
     * This value has no effect if a server is specified using the HTTP protocol (i.e. has SSL disabled).
     *
     * @since 4.2
     */
    public boolean isAllowUntrustedServer() {
        return allowUntrustedServer;
    }

    /**
     * Specifies whether it is acceptable to communicate with an HTTP build cache backend with an untrusted SSL certificate.
     * <p>
     * The SSL certificate for the HTTP build cache backend may be untrusted since it is internally provisioned or a self-signed certificate.
     * <p>
     * In such a scenario, you can either configure the build JVM environment to trust the certificate,
     * or set this property to {@code true} to disable verification of the server's identity.
     * <p>
     * Allowing communication with untrusted servers keeps data encrypted during transmission,
     * but makes it easier for a man-in-the-middle to impersonate the intended server and capture data.
     * <p>
     * This value has no effect if a server is specified using the HTTP protocol (i.e. has SSL disabled).
     *
     * @since 4.2
     */
    public void setAllowUntrustedServer(boolean allowUntrustedServer) {
        this.allowUntrustedServer = allowUntrustedServer;
    }
}
