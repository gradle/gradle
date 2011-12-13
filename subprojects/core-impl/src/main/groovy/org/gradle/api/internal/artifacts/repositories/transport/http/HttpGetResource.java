/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.transport.http;

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.ivy.plugins.repository.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

class HttpGetResource extends AbstractHttpResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpGetResource.class);

    private final String source;
    private final GetMethod method;

    public HttpGetResource(String source, GetMethod method) {
        this.source = source;
        this.method = method;
    }

    public String getName() {
        return source;
    }

    @Override
    public String toString() {
        return "HttpResource: " + getName();
    }

    public long getLastModified() {
        Header responseHeader = method.getResponseHeader("last-modified");
        if (responseHeader == null) {
            return 0;
        }
        try {
            return Date.parse(responseHeader.getValue());
        } catch (Exception e) {
            return 0;
        }
    }

    public long getContentLength() {
        return method.getResponseContentLength();
    }

    public boolean exists() {
        return true;
    }

    public boolean isLocal() {
        return false;
    }

    public Resource clone(String cloneName) {
        throw new UnsupportedOperationException();
    }

    public InputStream openStream() throws IOException {
        LOGGER.debug("Attempting to download resource {}.", source);
        return method.getResponseBodyAsStream();
    }
}
