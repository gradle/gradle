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

import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpRequestRetryHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.ProxySelectorRoutePlanner;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.plugins.repository.AbstractRepository;
import org.apache.ivy.plugins.repository.BasicResource;
import org.apache.ivy.plugins.repository.RepositoryCopyProgressListener;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.TransferEvent;
import org.apache.ivy.util.url.ApacheURLLister;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.artifacts.repositories.PasswordCredentials;
import org.gradle.api.internal.artifacts.ivyservice.filestore.CachedArtifact;
import org.gradle.api.internal.artifacts.ivyservice.filestore.ExternalArtifactCache;
import org.gradle.api.internal.artifacts.repositories.transport.ResourceCollection;
import org.gradle.internal.UncheckedException;
import org.gradle.util.GUtil;
import org.gradle.util.GradleVersion;
import org.jfrog.wharf.ivy.checksum.ChecksumType;
import org.jfrog.wharf.ivy.util.WharfUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.ProxySelector;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * A repository which uses commons-httpclient to access resources using HTTP/HTTPS.
 */
public class HttpResourceCollection extends AbstractRepository implements ResourceCollection {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceCollection.class);
    private final DefaultHttpClient client = new DefaultHttpClient();
    private final RepositoryCopyProgressListener progress = new RepositoryCopyProgressListener(this);
    private final List<HttpResource> openResources = new ArrayList<HttpResource>();

    private final ExternalArtifactCache externalArtifactCache;
    private final UsernamePasswordCredentials httpClientCredentials;

    public HttpResourceCollection(HttpSettings httpSettings, ExternalArtifactCache externalArtifactCache) {
        PasswordCredentials credentials = httpSettings.getCredentials();
        if (GUtil.isTrue(credentials.getUsername())) {
            httpClientCredentials = new UsernamePasswordCredentials(credentials.getUsername(), credentials.getPassword());
        } else {
            httpClientCredentials = null;
        }
        this.externalArtifactCache = externalArtifactCache;

        // Use standard JVM proxy settings
        ProxySelectorRoutePlanner routePlanner = new ProxySelectorRoutePlanner(client.getConnectionManager().getSchemeRegistry(), ProxySelector.getDefault());
        client.setRoutePlanner(routePlanner);

        client.setHttpRequestRetryHandler(new HttpRequestRetryHandler() {
            public boolean retryRequest(IOException exception, int executionCount, HttpContext context) {
                return false;
            }
        });
    }

    public HttpResource getResource(String source) throws IOException {
        return getResource(source, null);
    }

    public HttpResource getResource(final String source, ArtifactRevisionId artifactId) throws IOException {
        return getResource(source, artifactId, true);
    }

    public HttpResource getResource(String source, ArtifactRevisionId artifactId, boolean forDownload) throws IOException {
        abortOpenResources();
        if (forDownload) {
            HttpResource httpResource = initGet(source, artifactId);
            return recordOpenGetResource(httpResource);
        }
        return initHead(source);
    }

    private void abortOpenResources() {
        for (HttpResource openResource : openResources) {
            LOGGER.warn("Forcing close on abandoned resource: " + openResource);
            openResource.close();
        }
        openResources.clear();
    }

    private HttpResource recordOpenGetResource(HttpResource httpResource) {
        if (httpResource instanceof HttpResponseResource) {
            openResources.add(httpResource);
        }
        return httpResource;
    }

    private HttpResource initGet(String source, ArtifactRevisionId artifactId) {
        LOGGER.debug("Constructing GET resource: {}", source);
        
        List<CachedArtifact> candidateArtifacts = new ArrayList<CachedArtifact>();
        externalArtifactCache.addMatchingCachedArtifacts(artifactId, candidateArtifacts);

        // First see if we can use any of the candidates directly.
        if (candidateArtifacts.size() > 0) {
            CachedHttpResource cachedResource = findCachedResource(source, candidateArtifacts);
            if (cachedResource != null) {
                return cachedResource;
            }
        }

        HttpGet request = new HttpGet(source);
        return processHttpRequest(source, request);
    }

    private HttpResource initHead(String source) {
        LOGGER.debug("Constructing HEAD resource: {}", source);
        HttpHead request = new HttpHead(source);
        return processHttpRequest(source, request);
    }

    private HttpResource processHttpRequest(String source, HttpRequestBase request) {
        String method = request.getMethod();
        configureMethod(request);
        HttpResponse response;
        try {
            response = executeMethod(request);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not %s '%s'.", method, source), e);
        }
        if (wasMissing(response)) {
            LOGGER.info("Resource missing. [HTTP {}: {}]", method, source);
            return new MissingHttpResource(source);
        }
        if (!wasSuccessful(response)) {
            LOGGER.info("Failed to get resource: {}. [HTTP {}: {}]", new Object[]{method, response.getStatusLine(), source});
            throw new UncheckedIOException(String.format("Could not %s '%s'. Received status code %s from server: %s",
                                                         method, source, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }
        LOGGER.info("Resource found. [HTTP {}: {}]", method, source);
        return new HttpResponseResource(method, source, response);
    }

    private CachedHttpResource findCachedResource(String source, List<CachedArtifact> candidates) {
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
        HttpGet get = new HttpGet(checksumUrl);
        configureMethod(get);
        try {
            HttpResponse httpResponse = executeMethod(get);
            if (wasSuccessful(httpResponse)) {
                String checksumValue = EntityUtils.toString(httpResponse.getEntity());
                return WharfUtils.getCleanChecksum(checksumValue);
            }
            if (!wasMissing(httpResponse)) {
                LOGGER.info("Request for checksum at {} failed: {}", checksumUrl, httpResponse.getStatusLine());
            }
            return null;
        } catch (IOException e) {
            LOGGER.warn("Checksum missing at {} due to: {}", checksumUrl, e.getMessage());
            return null;
        } finally {
            // TODO:DAZ Don't need this
            get.abort();
        }
    }

    public void get(String source, File destination) throws IOException {
        throw new UnsupportedOperationException();
    }

    public void downloadResource(Resource res, File destination) throws IOException {
        if (!(res instanceof HttpResource)) {
            throw new IllegalArgumentException("Can only download HttpResource");
        }
        HttpResource resource = (HttpResource) res;
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
            openResources.remove(resource);
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
        HttpPut method = new HttpPut(destination);
        configureMethod(method);
        method.setEntity(new FileEntity(source, "application/octet-stream"));
        LOGGER.debug("Performing HTTP PUT: {}", method.getURI());
        HttpResponse response = client.execute(method);
        EntityUtils.consume(response.getEntity());
        if (!wasSuccessful(response)) {
            throw new IOException(String.format("Could not PUT '%s'. Received status code %s from server: %s",
                                                destination, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }
    }

    private void configureMethod(HttpRequest method) {
        method.addHeader("User-Agent", "Gradle/" + GradleVersion.current().getVersion());
        method.addHeader("Accept-Encoding", "identity");

        // Do preemptive authentication
        if (httpClientCredentials != null) {
            try {
                method.addHeader(new BasicScheme().authenticate(httpClientCredentials, method));
            } catch (AuthenticationException e) {
                throw UncheckedException.asUncheckedException(e);
            }
        }
    }

    private HttpResponse executeMethod(HttpUriRequest method) throws IOException {
        LOGGER.debug("Performing HTTP GET: {}", method.getURI());
        HttpResponse httpResponse = client.execute(method);
        // Consume content for non-successful, responses. This avoids the connection being left open.
        if (!wasSuccessful(httpResponse)) {
            EntityUtils.consume(httpResponse.getEntity());
            return httpResponse;
        }
        return httpResponse;
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

    private boolean wasMissing(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode == 404;
    }

    private boolean wasSuccessful(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode >= 200 && statusCode < 300;
    }
}
