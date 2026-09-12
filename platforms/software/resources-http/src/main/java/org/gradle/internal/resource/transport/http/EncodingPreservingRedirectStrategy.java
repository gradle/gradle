/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.internal.resource.transport.http;

import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.client.CircularRedirectException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.client.RedirectLocations;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Asserts;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Follows a redirect to the location the server gave, without re-encoding it.
 *
 * <p>httpclient 4.5.14 runs the {@code Location} header through
 * {@link URIUtils#normalizeSyntax(URI)}, which decodes the path into segments and encodes them
 * again, escaping only what RFC 3986 requires. A server that answered {@code %2B} is then asked
 * for {@code +}.
 *
 * <p>Skipping that call also skips what else it did: an absolute location keeps its dot segments
 * and the case of its scheme and host. A relative one goes through {@link URI#resolve(URI)},
 * which removes dot segments under RFC 3986.
 */
@NullMarked
class EncodingPreservingRedirectStrategy extends DefaultRedirectStrategy {

    private static final Logger LOGGER =
        LoggerFactory.getLogger(EncodingPreservingRedirectStrategy.class);

    @Override
    public URI getLocationURI(
        HttpRequest request,
        HttpResponse response,
        HttpContext context
    ) throws ProtocolException {
        HttpClientContext clientContext = HttpClientContext.adapt(context);
        Header location = response.getFirstHeader(HttpHeaders.LOCATION);
        if (location == null) {
            throw new ProtocolException(
                "Received redirect response " + response.getStatusLine() + " but no location header"
            );
        }
        LOGGER.debug("Redirect requested to location '{}'", location.getValue());

        RequestConfig requestConfig = clientContext.getRequestConfig();
        URI targetUri = createLocationURI(location.getValue());
        if (!targetUri.isAbsolute()) {
            targetUri = resolveAgainstRequest(targetUri, request, clientContext, requestConfig);
        }

        recordVisit(targetUri, clientContext, requestConfig);
        return targetUri;
    }

    private URI resolveAgainstRequest(
        URI location, HttpRequest request,
        HttpClientContext clientContext,
        RequestConfig requestConfig
    ) throws ProtocolException {
        if (!requestConfig.isRelativeRedirectsAllowed()) {
            throw new ProtocolException(
                "Relative redirect location '" + location + "' not allowed"
            );
        }
        HttpHost host = clientContext.getTargetHost();
        Asserts.notNull(host, "Target host");
        try {
            URI requestedUri = new URI(request.getRequestLine().getUri());
            // URIUtils.resolve is where a relative location loses its escaping.
            URI base = URIUtils.rewriteURI(requestedUri, host, URIUtils.NO_FLAGS);
            return resolveReference(base, location);
        } catch (URISyntaxException e) {
            throw new ProtocolException(e.getMessage(), e);
        }
    }

    private static URI resolveReference(URI base, URI reference) {
        String ref = reference.toASCIIString();
        if (!ref.isEmpty() && !ref.startsWith("?")) {
            return base.resolve(reference);
        }
        String target = truncateAt(base.toASCIIString(), '#');
        return URI.create(ref.isEmpty() ? target : truncateAt(target, '?') + ref);
    }

    private static String truncateAt(String uri, char marker) {
        int index = uri.indexOf(marker);
        return index < 0 ? uri : uri.substring(0, index);
    }

    private void recordVisit(
        URI target,
        HttpClientContext clientContext,
        RequestConfig config
    ) throws ProtocolException {
        RedirectLocations visited =
            (RedirectLocations) clientContext.getAttribute(HttpClientContext.REDIRECT_LOCATIONS);
        if (visited == null) {
            visited = new RedirectLocations();
            clientContext.setAttribute(HttpClientContext.REDIRECT_LOCATIONS, visited);
        }
        if (!config.isCircularRedirectsAllowed() && visited.contains(target)) {
            throw new CircularRedirectException("Circular redirect to '" + target + "'");
        }
        visited.add(target);
    }
}
