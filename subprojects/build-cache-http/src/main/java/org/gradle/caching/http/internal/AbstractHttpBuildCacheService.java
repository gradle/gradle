/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.http.internal;

import org.apache.http.client.utils.URIBuilder;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.transport.http.HttpClientHelper;

import java.io.Closeable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

public abstract class AbstractHttpBuildCacheService implements Closeable {

    protected final URI root;
    protected final boolean useExpectContinue;
    protected final HttpBuildCacheRequestCustomizer requestCustomizer;
    protected final HttpClientHelper httpClientHelper;

    protected AbstractHttpBuildCacheService(URI url, boolean useExpectContinue, HttpClientHelper httpClientHelper, HttpBuildCacheRequestCustomizer requestCustomizer) {
        this.useExpectContinue = useExpectContinue;
        this.root = withTrailingSlash(url);
        this.httpClientHelper = httpClientHelper;
        this.requestCustomizer = requestCustomizer;
    }

    /**
     * Create a safe URI from the given one by stripping out user info.
     *
     * @param uri Original URI
     * @return a new URI with no user info
     */
    protected static URI safeUri(URI uri) {
        try {
            return new URI(uri.getScheme(), null, uri.getHost(), uri.getPort(), uri.getPath(), uri.getQuery(), uri.getFragment());
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    /**
     * Add a trailing slash to the given URI's path if necessary.
     *
     * @param uri the original URI
     * @return a URI guaranteed to have a trailing slash in the path
     */
    private static URI withTrailingSlash(URI uri) {
        if (uri.getPath().endsWith("/")) {
            return uri;
        }
        try {
            return new URIBuilder(uri).setPath(uri.getPath() + "/").build();
        } catch (URISyntaxException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    protected static boolean isHttpSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
    }

    @Override
    public void close() throws IOException {
        httpClientHelper.close();
    }
}
