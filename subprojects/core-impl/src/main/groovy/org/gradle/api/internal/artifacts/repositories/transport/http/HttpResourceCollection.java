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

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.ContentEncodingHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultRedirectStrategy;
import org.apache.http.impl.cookie.DateUtils;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.plugins.repository.*;
import org.apache.ivy.util.url.ApacheURLLister;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.internal.artifacts.repositories.transport.ResourceCollection;
import org.gradle.api.internal.externalresource.*;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResource;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceCandidates;
import org.gradle.api.internal.externalresource.local.LocallyAvailableResourceFinder;
import org.gradle.internal.UncheckedException;
import org.gradle.util.hash.HashValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * A repository which uses commons-httpclient to access resources using HTTP/HTTPS.
 */
public class HttpResourceCollection extends AbstractRepository implements ResourceCollection {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpResourceCollection.class);
    private final DefaultHttpClient client = new ContentEncodingHttpClient();
    private final BasicHttpContext httpContext = new BasicHttpContext();
    private final RepositoryCopyProgressListener progress = new RepositoryCopyProgressListener(this);
    private final List<ExternalResource> openResources = new ArrayList<ExternalResource>();


    private final HttpClientConfigurer configurer;
    private final LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder;
    private final CachedExternalResourceIndex<String> byUrlCachedExternalResourceIndex;

    public HttpResourceCollection(HttpSettings httpSettings, 
                                  LocallyAvailableResourceFinder<ArtifactRevisionId> locallyAvailableResourceFinder,
                                  CachedExternalResourceIndex<String> byUrlCachedExternalResourceIndex) {
        configurer = new HttpClientConfigurer(httpSettings);
        configurer.configure(client);

        this.locallyAvailableResourceFinder = locallyAvailableResourceFinder;
        this.byUrlCachedExternalResourceIndex = byUrlCachedExternalResourceIndex;
    }

    public ExternalResource getResource(String source) throws IOException {
        return getResource(source, null);
    }

    public ExternalResource getResource(final String source, ArtifactRevisionId artifactId) throws IOException {
        return getResource(source, artifactId, true);
    }

    public ExternalResource getResource(String source, ArtifactRevisionId artifactId, boolean forDownload) throws IOException {
        abortOpenResources();
        if (forDownload) {
            ExternalResource httpResource = initGet(source, artifactId);
            return recordOpenGetResource(httpResource);
        }
        return initHead(source);
    }

    private void abortOpenResources() {
        for (ExternalResource openResource : openResources) {
            LOGGER.warn("Forcing close on abandoned resource: " + openResource);
            try {
                openResource.close();
            } catch (IOException e) {
                LOGGER.warn("Failed to close abandoned resource", e);
            }
        }
        openResources.clear();
    }

    private ExternalResource recordOpenGetResource(ExternalResource httpResource) {
        if (httpResource instanceof HttpResponseResource) {
            openResources.add(httpResource);
        }
        return httpResource;
    }

    private ExternalResource initGet(String source, ArtifactRevisionId artifactId) {
        LOGGER.debug("Constructing GET resource: {}", source);

        // Do we have any artifacts in the cache with the same checksum
        LocallyAvailableResourceCandidates localCandidates = locallyAvailableResourceFinder.findCandidates(artifactId);
        if (!localCandidates.isNone()) {
            ExternalResource cachedResource = findCachedResourceBySha1(source, localCandidates);
            if (cachedResource != null) {
                return cachedResource;
            }
        }

        HttpGet request = new HttpGet(source);
        return processHttpRequest(source, request, byUrlCachedExternalResourceIndex.lookup(source));
    }

    private ExternalResource initHead(String source) {
        LOGGER.debug("Constructing HEAD resource: {}", source);
        HttpHead request = new HttpHead(source);
        return processHttpRequest(source, request, null);
    }

    private ExternalResource processHttpRequest(String source, HttpRequestBase request, CachedExternalResource cachedExternalResource) {
        String method = request.getMethod();
        configurer.configureMethod(request);

        if (cachedExternalResource != null && cachedExternalResource.getExternalResourceMetaData() != null && cachedExternalResource.getExternalResourceMetaData().getLastModified() != null) {
            String formattedDate = DateUtils.formatDate(cachedExternalResource.getExternalResourceMetaData().getLastModified());
            LOGGER.info("Adding If-Modified-Since: {}. [HTTP {}: {}]", new Object[]{formattedDate, method, source});
            request.addHeader("If-Modified-Since", formattedDate);
        }

        HttpResponse response;
        try {
            response = executeGetOrHead(request);
        } catch (IOException e) {
            throw new UncheckedIOException(String.format("Could not %s '%s'.", method, source), e);
        }

        if (wasMissing(response)) {
            LOGGER.info("Resource missing. [HTTP {}: {}]", method, source);
            return new MissingExternalResource(source);
        }
        if (cachedExternalResource != null && wasUnmodified(response)) {
            LOGGER.info("Resource was unmodified. [HTTP {}: {}]", method, source);
            return new CachedExternalResourceAdapter(source, cachedExternalResource, HttpResourceCollection.this);
        }
        if (!wasSuccessful(response)) {
            LOGGER.info("Failed to get resource: {}. [HTTP {}: {}]", new Object[]{method, response.getStatusLine(), source});
            throw new UncheckedIOException(String.format("Could not %s '%s'. Received status code %s from server: %s",
                                                         method, source, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }
        LOGGER.info("Resource found. [HTTP {}: {}]", method, source);
        return new HttpResponseResource(method, source, response) {
            @Override
            public void close() throws IOException {
                super.close();
                HttpResourceCollection.this.openResources.remove(this);
            }
        };
    }

    private ExternalResource findCachedResourceBySha1(String source, LocallyAvailableResourceCandidates candidates) {
        String checksumType = "SHA-1";
        String checksumUrl = source + ".sha1";

        HashValue sha1 = downloadSha1(checksumUrl);
        if (sha1 == null) {
            LOGGER.info("Checksum {} unavailable. [HTTP GET: {}]", checksumType, checksumUrl);
        } else {
            LocallyAvailableResource locallyAvailable = candidates.findByHashValue(sha1);
            if (locallyAvailable != null) {
                LOGGER.info("Checksum {} matched cached resource: [HTTP GET: {}]", checksumType, checksumUrl);
                return new LocallyAvailableExternalResource(source, locallyAvailable, HttpResourceCollection.this);
            }

            LOGGER.info("Checksum {} did not match cached resources: [HTTP GET: {}]", checksumType, checksumUrl);
        }
        return null;
    }

    private HashValue downloadSha1(String checksumUrl) {
        HttpGet get = new HttpGet(checksumUrl);
        configurer.configureMethod(get);
        try {
            HttpResponse httpResponse = executeGetOrHead(get);
            if (wasSuccessful(httpResponse)) {
                String checksumValue = EntityUtils.toString(httpResponse.getEntity());
                return HashValue.parse(checksumValue);
            }
            if (!wasMissing(httpResponse)) {
                LOGGER.info("Request for checksum at {} failed: {}", checksumUrl, httpResponse.getStatusLine());
            }
            return null;
        } catch (Exception e) {
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
        if (!(res instanceof ExternalResource)) {
            throw new IllegalArgumentException("Can only download ExternalResource");
        }
        ExternalResource resource = (ExternalResource) res;
        fireTransferInitiated(resource, TransferEvent.REQUEST_GET);
        try {
            progress.setTotalLength(resource.getContentLength() > 0 ? resource.getContentLength() : null);
            resource.writeTo(destination, progress);
        } catch (IOException e) {
            fireTransferError(e);
            throw e;
        } catch (Exception e) {
            fireTransferError(e);
            throw UncheckedException.throwAsUncheckedException(e);
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
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            progress.setTotalLength(null);
        }
    }

    private void doPut(File source, String destination) throws IOException {
        HttpPut method = new HttpPut(destination);
        configurer.configureMethod(method);
        method.setEntity(new FileEntity(source, "application/octet-stream"));
        HttpResponse response = performHttpRequest(method);
        EntityUtils.consume(response.getEntity());
        if (!wasSuccessful(response)) {
            throw new IOException(String.format("Could not PUT '%s'. Received status code %s from server: %s",
                                                destination, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }
    }

    private HttpResponse executeGetOrHead(HttpRequestBase method) throws IOException {
        HttpResponse httpResponse = performHttpRequest(method);
        // Consume content for non-successful, responses. This avoids the connection being left open.
        if (!wasSuccessful(httpResponse)) {
            EntityUtils.consume(httpResponse.getEntity());
            return httpResponse;
        }
        return httpResponse;
    }

    private HttpResponse performHttpRequest(HttpRequestBase request) throws IOException {
        // Without this, HTTP Client prohibits multiple redirects to the same location within the same context
        httpContext.removeAttribute(DefaultRedirectStrategy.REDIRECT_LOCATIONS);

        LOGGER.debug("Performing HTTP {}: {}", request.getMethod(), request.getURI());
        return client.execute(request, httpContext);
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

    private boolean wasUnmodified(HttpResponse response) {
        int statusCode = response.getStatusLine().getStatusCode();
        return statusCode == 304;
    }

}
