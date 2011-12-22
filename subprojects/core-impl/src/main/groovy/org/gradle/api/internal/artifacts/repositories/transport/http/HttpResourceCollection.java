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

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.io.output.CloseShieldOutputStream;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.plugins.repository.*;
import org.apache.ivy.util.FileUtil;
import org.apache.ivy.util.url.ApacheURLLister;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.ivyservice.filestore.CachedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.filestore.ExternalArtifactCache;
import org.gradle.api.internal.artifacts.repositories.transport.ResourceCollection;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.gradle.util.UncheckedException;
import org.jfrog.wharf.ivy.checksum.ChecksumType;
import org.jfrog.wharf.ivy.util.WharfUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A repository which uses commons-httpclient to access resources using HTTP/HTTPS.
 */
public class HttpResourceCollection extends AbstractRepository implements ResourceCollection {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceCollection.class);
    private final Map<String, HttpResource> resources = new HashMap<String, HttpResource>();
    private final HttpClient client = new HttpClient();
    private final HttpProxySettings proxySettings;
    private final RepositoryCopyProgressListener progress = new RepositoryCopyProgressListener(this);

    private final ExternalArtifactCache externalArtifactCache;

    public HttpResourceCollection(HttpSettings httpSettings, ExternalArtifactCache externalArtifactCache) {
        PasswordCredentials credentials = httpSettings.getCredentials();
        if (GUtil.isTrue(credentials.getUsername())) {
            client.getParams().setAuthenticationPreemptive(true);
            client.getState().setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword()));
        }
        this.proxySettings = httpSettings.getProxySettings();
        this.externalArtifactCache = externalArtifactCache;
    }

    public HttpResource getResource(final String source, ArtifactRevisionId artifactId) throws IOException {
        LOGGER.debug("Constructing GET resource: {}", source);

        List<CachedArtifact> cachedArtifacts = new ArrayList<CachedArtifact>();
        externalArtifactCache.addMatchingCachedArtifacts(artifactId, cachedArtifacts);

        releasePriorResources();
        GetMethod method = new GetMethod(source);
        configureMethod(method);
        HttpResource resource = createLazyResource(source, method, cachedArtifacts);
        resources.put(source, resource);
        return resource;
    }

    public HttpResource getResource(final String source) throws IOException {
        return getResource(source, null);
    }

    private void releasePriorResources() {
        for (Resource resource : resources.values()) {
            LazyResourceInvocationHandler invocationHandler = (LazyResourceInvocationHandler) Proxy.getInvocationHandler(resource);
            invocationHandler.release();
        }
    }

    private HttpResource createLazyResource(String source, GetMethod method, List<CachedArtifact> artifacts) {
        LazyResourceInvocationHandler invocationHandler = new LazyResourceInvocationHandler(source, method, artifacts);
        return HttpResource.class.cast(Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{HttpResource.class}, invocationHandler));
    }

    public void get(String source, File destination) throws IOException {
        HttpResource resource = resources.get(source);
        fireTransferInitiated(resource, TransferEvent.REQUEST_GET);
        try {
            progress.setTotalLength(resource.getContentLength());
            resource.writeTo(destination, progress);
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
        method.setRequestHeader("Accept-Encoding", "identity");
        method.getParams().setParameter(HttpMethodParams.RETRY_HANDLER, new HttpMethodRetryHandler() {
            public boolean retryMethod(HttpMethod method, IOException exception, int executionCount) {
                return false;
            }
        });
    }

    private int executeMethod(HttpMethod method) throws IOException {
        LOGGER.debug("Performing HTTP GET: {}", method.getURI());
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
        private final List<CachedArtifact> candidates;
        private Resource delegate;

        private LazyResourceInvocationHandler(String source, GetMethod method, List<CachedArtifact> candidates) {
            this.method = method;
            this.source = source;
            this.candidates = candidates;
        }

        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (delegate == null) {
                delegate = init();
            }
            try {
                return method.invoke(delegate, args);
            } catch (InvocationTargetException e) {
                throw UncheckedException.asUncheckedException(e.getTargetException());
            }
        }

        private Resource init() {
            // First see if we can use any of the candidates directly.
            if (candidates.size() > 0) {
                CachedHttpResource cachedResource = findCachedResource();
                if (cachedResource != null) {
                    return cachedResource;
                }
            }

            int result;
            try {
                result = executeMethod(method);
            } catch (IOException e) {
                throw new UncheckedIOException(String.format("Could not GET '%s'.", source), e);
            }
            if (result == 404) {
                LOGGER.info("Resource missing. [HTTP GET: {}]", source);
                return new MissingHttpResource(source);
            }
            if (!wasSuccessful(result)) {
                LOGGER.info("Failed to get resource: {} ({}). [HTTP GET: {}]", new Object[]{result, method.getStatusText(), source});
                throw new UncheckedIOException(String.format("Could not GET '%s'. Received status code %s from server: %s", source, result, method.getStatusText()));
            }
            LOGGER.info("Resource found. [HTTP GET: {}]", source);
            return new HttpGetResource(source, method);
        }

        private CachedHttpResource findCachedResource() {
            ChecksumType checksumType = ChecksumType.sha1;
            String checksumUrl = source + checksumType.ext();

            String sha1 = downloadChecksum(checksumUrl);
            if (sha1 == null) {
                LOGGER.info("Checksum {} unavailable. [HTTP GET: {}]", checksumType, checksumUrl);
            } else {
                for (CachedArtifact candidate : candidates) {
                    if (candidate.getSha1().equals(sha1)) {
                        LOGGER.info("Checksum {} matched cached resource: [HTTP GET: {}]", checksumType, checksumUrl);
                        return new CachedHttpResource(source, candidate, HttpResourceCollection.this);
                    }
                }
                LOGGER.info("Checksum {} did not match cached resources: [HTTP GET: {}]", checksumType, checksumUrl);
            }
            return null;
        }

        private String downloadChecksum(String checksumUrl) {
            GetMethod get = new GetMethod(checksumUrl);
            configureMethod(get);
            try {
                int result = executeMethod(get);
                if (wasSuccessful(result)) {
                    return WharfUtils.getCleanChecksum(get.getResponseBodyAsString());
                }
                if (result != 404) {
                    LOGGER.info("Request for checksum at {} failed: {}", checksumUrl, get.getStatusText());
                }
                return null;
            } catch (IOException e) {
                LOGGER.warn("Checksum missing at {} due to: {}", checksumUrl, e.getMessage());
                return null;
            } finally {
                get.releaseConnection();
            }
        }

        public void release() {
            if (delegate != null && delegate.exists()) {
                if (method != null) {
                    method.releaseConnection();
                }
                delegate = null;
            }
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
