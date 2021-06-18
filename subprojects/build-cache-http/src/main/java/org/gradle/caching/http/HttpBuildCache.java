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
import org.gradle.api.Incubating;
import org.gradle.caching.configuration.AbstractBuildCache;

import javax.annotation.Nullable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * Configuration object for the HTTP build cache.
 *
 * Cache entries are loaded via {@literal GET} and stored via {@literal PUT} requests.
 * <p>
 * A successful {@literal GET} request must return a response with status {@literal 200} (cache hit) or {@literal 404} (cache miss),
 * with cache hit responses including the cache entry as the response body.
 * A successful {@literal PUT} request must return any 2xx response.
 * <p>
 * {@literal PUT} requests may also return a {@literal 413 Payload Too Large} response to indicate that the payload is larger than can be accepted.
 * This is useful when {@link #isUseExpectContinue()} is enabled.
 * <p>
 * Redirecting responses may be issued with {@literal 301}, {@literal 302}, {@literal 303}, {@literal 307} or {@literal 308} responses.
 * Redirecting responses to {@literal PUT} requests must use {@literal 307} or {@literal 308} to have the {@literal PUT} replayed.
 * Otherwise, the redirect will be followed with a {@literal GET} request.
 * <p>
 * Any other type of response will be treated as an error, causing the remote cache to be disabled for the remainder of the build.
 * <p>
 * When credentials are configured (see {@link #getCredentials()}), they are sent using HTTP Basic Auth.
 * <p>
 * Requests that fail during request transmission, after having established a TCP connection, will automatically be retried.
 * This includes dropped connections, read or write timeouts, and low level network failures such as a connection resets.
 * Requests will be retried 3 times, before giving up and disabling use of the cache for the remainder of the build.
 *
 * @since 3.5
 */
public class HttpBuildCache extends AbstractBuildCache {
    private final HttpBuildCacheCredentials credentials;
    private URI url;
    private boolean allowUntrustedServer;
    private boolean allowInsecureProtocol;
    private boolean useExpectContinue;

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
     *
     * @see #isAllowUntrustedServer()
     * @since 4.2
     */
    public void setAllowUntrustedServer(boolean allowUntrustedServer) {
        this.allowUntrustedServer = allowUntrustedServer;
    }

    /**
     * Specifies whether it is acceptable to communicate with a build cache over an insecure HTTP connection.
     * <p>
     * For security purposes this intentionally requires a user to opt-in to using insecure protocols on case by case basis.
     * <p>
     * Gradle intentionally does not offer a global system/gradle property that allows a universal disable of this check.
     * <p>
     * <b>Allowing communication over insecure protocols allows for a man-in-the-middle to impersonate the intended server,
     * and gives an attacker the ability to
     * <a href="https://max.computer/blog/how-to-take-over-the-computer-of-any-java-or-clojure-or-scala-developer/">serve malicious executable code onto the system.</a>
     * </b>
     * <p>
     * See also:
     * <a href="https://medium.com/bugbountywriteup/want-to-take-over-the-java-ecosystem-all-you-need-is-a-mitm-1fc329d898fb">Want to take over the Java ecosystem? All you need is a MITM!</a>
     *
     * @since 6.0
     */
    public boolean isAllowInsecureProtocol() {
        return allowInsecureProtocol;
    }

    /**
     * Specifies whether it is acceptable to communicate with a build cache over an insecure HTTP connection.
     *
     * @see #isAllowInsecureProtocol()
     * @since 6.0
     */
    public void setAllowInsecureProtocol(boolean allowInsecureProtocol) {
        this.allowInsecureProtocol = allowInsecureProtocol;
    }

    /**
     * Specifies whether HTTP expect-continue should be used for store requests.
     *
     * @since 7.2
     */
    @Incubating
    public void setUseExpectContinue(boolean useExpectContinue) {
        this.useExpectContinue = useExpectContinue;
    }

    /**
     * Specifies whether HTTP expect-continue should be used for store requests.
     *
     * This value defaults to {@code false}.
     *
     * When enabled, whether or not a store request would succeed is checked with the server before attempting.
     * This is particularly useful when potentially dealing with large artifacts that may be rejected by the server with a {@literal 413 Payload Too Large} response,
     * as it avoids the overhead of transmitting the large file just to have it rejected.
     * This fail-fast behavior comes at the expense of extra marginal overhead for successful requests,
     * due to the extra network communication required by the initial check.
     *
     * Note: not all HTTP servers support expect-continue.
     *
     * @see #setUseExpectContinue(boolean)
     * @since 7.2
     */
    @Incubating
    public boolean isUseExpectContinue() {
        return useExpectContinue;
    }
}
