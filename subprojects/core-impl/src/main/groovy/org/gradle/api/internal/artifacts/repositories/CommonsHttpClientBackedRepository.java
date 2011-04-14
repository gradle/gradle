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
package org.gradle.api.internal.artifacts.repositories;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.io.IOUtils;
import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.Resource;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A repository which uses commons-httpclient to access resources using HTTP/HTTPS.
 */
public class CommonsHttpClientBackedRepository extends AbstractRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonsHttpClientBackedRepository.class);
    private final Map<String, HttpResource> resources = new HashMap<String, HttpResource>();
    private final HttpClient client = new HttpClient();

    public CommonsHttpClientBackedRepository(String username, String password) {
        if (GUtil.isTrue(username)) {
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(new AuthScope(null, -1, null), new UsernamePasswordCredentials(username, password));
        }
    }

    public void get(String source, File destination) throws IOException {
        HttpResource resource = resources.get(source);
        resource.downloadTo(destination);
    }

    public Resource getResource(final String source) throws IOException {
        LOGGER.debug("Attempting to get resource {}.", source);

        GetMethod method = new GetMethod(source);
        configureMethod(method);
        int result = client.executeMethod(method);
        if (result == 404) {
            return new MissingResource(source);
        }
        if (result != 200) {
            throw new IOException(String.format("Could not GET '%s'. Received status code %s from server: %s", source, result, method.getStatusText()));
        }

        HttpResource resource = new HttpResource(source, method);
        resources.put(source, resource);
        return resource;
    }

    @Override
    protected void put(File source, String destination, boolean overwrite) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        PutMethod method = new PutMethod(destination);
        configureMethod(method);
        int result = client.executeMethod(method);
        if (result != 200) {
            throw new IOException(String.format("Could not PUT '%s'. Received status code %s from server: %s", destination, result, method.getStatusText()));
        }
    }

    private void configureMethod(HttpMethod method) {
        method.setRequestHeader("User-Agent", "Gradle/" + GradleVersion.current().getVersion());
    }

    public List list(String parent) throws IOException {
        return Collections.EMPTY_LIST;
    }

    private static class HttpResource implements Resource {
        private final String source;
        private final GetMethod method;

        public HttpResource(String source, GetMethod method) {
            this.source = source;
            this.method = method;
        }

        public String getName() {
            return source;
        }

        @Override
        public String toString() {
            return getName();
        }

        public long getLastModified() {
            return 0;
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
            return method.getResponseBodyAsStream();
        }

        public void downloadTo(File destination) throws IOException {
            FileOutputStream output = new FileOutputStream(destination);
            try {
                InputStream input = openStream();
                try {
                    IOUtils.copyLarge(input, output);
                } finally {
                    input.close();
                }
            } finally {
                output.close();
            }
        }
    }

    private static class MissingResource implements Resource {
        private final String source;

        public MissingResource(String source) {
            this.source = source;
        }

        public Resource clone(String cloneName) {
            throw new UnsupportedOperationException();
        }

        public String getName() {
            return source;
        }

        public long getLastModified() {
            throw new UnsupportedOperationException();
        }

        public long getContentLength() {
            throw new UnsupportedOperationException();
        }

        public boolean exists() {
            return false;
        }

        public boolean isLocal() {
            throw new UnsupportedOperationException();
        }

        public InputStream openStream() throws IOException {
            throw new UnsupportedOperationException();
        }
    }
}
