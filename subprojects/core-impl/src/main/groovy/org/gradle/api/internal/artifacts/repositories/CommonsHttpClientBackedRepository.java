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

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.ivy.plugins.repository.*;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.url.ApacheURLLister;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.repositories.transport.HttpProxySettings;
import org.gradle.api.internal.artifacts.repositories.transport.HttpSettings;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.*;

/**
 * A repository which uses commons-httpclient to access resources using HTTP/HTTPS.
 */
public class CommonsHttpClientBackedRepository extends AbstractRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonsHttpClientBackedRepository.class);
    private final Map<String, Resource> resources = new HashMap<String, Resource>();
    private final HttpClient client = new HttpClient();
    private final HttpProxySettings proxySettings;
    private final RepositoryCopyProgressListener progress = new RepositoryCopyProgressListener(this);

    public CommonsHttpClientBackedRepository(HttpSettings httpSettings) {
        PasswordCredentials credentials = httpSettings.getCredentials();
        if (GUtil.isTrue(credentials.getUsername())) {
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword()));
        }
        this.proxySettings = httpSettings.getProxySettings();
    }

    public Resource getResource(final String source) throws IOException {
        releasePriorResources();
        LOGGER.debug("Attempting to get resource {}.", source);
        GetMethod method = new GetMethod(source);
        configureMethod(method);
        Resource resource = createLazyResource(source, method);
        resources.put(source, resource);
        return resource;
    }

    private void releasePriorResources() {
        for (Resource resource : resources.values()) {
            LazyResourceInvocationHandler invocationHandler = (LazyResourceInvocationHandler) Proxy.getInvocationHandler(resource);
            invocationHandler.release();
        }
    }

    private Resource createLazyResource(String source, GetMethod method) {
        LazyResourceInvocationHandler invocationHandler = new LazyResourceInvocationHandler(source, method);
        return Resource.class.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Resource.class}, invocationHandler));
    }

    public void get(String source, File destination) throws IOException {
        Resource resource = resources.get(source);
        fireTransferInitiated(resource, TransferEvent.REQUEST_GET);
        try {
            progress.setTotalLength(resource.getContentLength());
            downloadResource(resource, destination);
        } catch (IOException e) {
            fireTransferError(e);
            throw e;
        } catch (Exception e) {
            fireTransferError(e);
            throw UncheckedException.asUncheckedException(e);
        } finally {
            progress.setTotalLength(null);
        }
    }

    public void downloadResource(Resource resource, File destination) throws IOException {
        FileOutputStream output = new FileOutputStream(destination);
        try {
            InputStream input = resource.openStream();
            try {
                FileUtil.copy(input, output, progress);
            } finally {
                input.close();
            }
        } finally {
            output.close();
        }
    }

    @Override
    protected void put(final File source, String destination, boolean overwrite) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        assert source.isFile();
        fireTransferInitiated(new BasicResource(destination, true, source.length(), source.lastModified(), false), TransferEvent.REQUEST_PUT);
        try {
            progress.setTotalLength(source.length());
            doPut(source, destination);
        } catch (IOException e) {
            fireTransferError(e);
            throw e;
        } catch (Exception e) {
            fireTransferError(e);
            throw UncheckedException.asUncheckedException(e);
        } finally {
            progress.setTotalLength(null);
        }
    }

    private void doPut(File source, String destination) throws IOException {
        PutMethod method = new PutMethod(destination);
        configureMethod(method);
        method.setRequestEntity(new FileRequestEntity(source));
        int result = executeMethod(method);
        if (!wasSuccessful(result)) {
            throw new IOException(String.format("Could not PUT '%s'. Received status code %s from server: %s", destination, result, method.getStatusText()));
        }
    }

    private void configureMethod(HttpMethod method) {
        method.setRequestHeader("User-Agent", "Gradle/" + GradleVersion.current().getVersion());
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
                return false;
            }
        });
    }

    private int executeMethod(HttpMethod method) throws IOException {
        configureProxyIfRequired(method);
        return client.executeMethod(method);
    }

    private void configureProxyIfRequired(HttpMethod method) throws URIException {
        HttpProxySettings.HttpProxy proxy = proxySettings.getProxy(method.getURI().getHost());
        if (proxy != null) {
            setProxyForClient(client, proxy);
        } else {
            client.getHostConfiguration().setProxyHost(null);
        }
    }

    private void setProxyForClient(HttpClient httpClient, HttpProxySettings.HttpProxy proxy) {
        // Only set proxy host once
        if (client.getHostConfiguration().getProxyHost() != null) {
            return;
        }
        httpClient.getHostConfiguration().setProxy(proxy.host, proxy.port);
        if (proxy.username != null) {
            httpClient.getState().setProxyCredentials(AuthScope.ANY, new UsernamePasswordCredentials(proxy.username, proxy.password));
        }
    }

    public List list(String parent) throws IOException {
        // Parse standard directory listing pages served up by Apache
        ApacheURLLister urlLister = new ApacheURLLister();
        List<URL> urls = urlLister.listAll(new URL(parent));
        if (urls != null) {
            List<String> ret = new ArrayList<String>(urls.size());
            for (URL url : urls) {
                ret.add(url.toExternalForm());
            }
            return ret;
        }
        return null;
    }

    private boolean wasSuccessful(int result) {
        return result >= 200 && result < 300;
    }

    private class LazyResourceInvocationHandler implements InvocationHandler {
        private final String source;
        private final GetMethod method;
        private Resource delegate;

        private LazyResourceInvocationHandler(String source, GetMethod method) {
            this.method = method;
            this.source = source;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (delegate == null) {
                delegate = init();
            }
            return method.invoke(delegate, args);
        }

        private Resource init() {
            LOGGER.info("Attempting to GET resource {}.", source);
            int result;
            try {
                result = executeMethod(method);
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not GET '%s'.", source), e);
            }
            if (result == 404) {
                LOGGER.debug("Resource missing: {}.", source);
                return new MissingResource(source);
            }
            if (!wasSuccessful(result)) {
                throw new UncheckedIOException(String.format("Could not GET '%s'. Received status code %s from server: %s", source, result, method.getStatusText()));
            }
            return new HttpResource(source, method);
        }

        public void release() {
            if (delegate != null && delegate.exists()) {
                method.releaseConnection();
                delegate = null;
            }
        }
    }

    private class HttpResource implements Resource {
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

    private class FileRequestEntity implements RequestEntity {
        private final File source;

        public FileRequestEntity(File source) {
            this.source = source;
        }

        public boolean isRepeatable() {
            return false;
        }

        public void writeRequest(OutputStream out) throws IOException {
            FileInputStream inputStream = new FileInputStream(source);
            try {
                FileUtil.copy(inputStream, new CloseShieldOutputStream(out), progress);
            } finally {
                inputStream.close();
            }
        }

        public long getContentLength() {
            return source.length();
        }

        public String getContentType() {
            return "application/octet-stream";
        }
    }
}
