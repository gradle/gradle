/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.caching.http.internal.httpclient;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpCoreContext;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.resource.transport.http.HttpClientUtil;
import org.gradle.internal.resource.transport.http.HttpRequestException;
import org.gradle.internal.resource.transport.http.HttpSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.gradle.internal.resource.transport.http.HttpClientUtil.effectiveUri;
import static org.gradle.internal.resource.transport.http.HttpClientUtil.stripUserCredentials;

public class HttpAsyncClientHelper implements Closeable {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpAsyncClientHelper.class);

    private final DocumentationRegistry documentationRegistry;
    private final HttpSettings settings;

    private CloseableHttpAsyncClient client;

    public HttpAsyncClientHelper(
        DocumentationRegistry documentationRegistry,
        HttpSettings settings
    ) {
        this.documentationRegistry = documentationRegistry;
        this.settings = settings;
    }

    private synchronized CloseableHttpAsyncClient getClient() {
        if (client == null) {
            this.client = createClient();
        }
        return client;
    }

    private CloseableHttpAsyncClient createClient() {
        HttpAsyncClientBuilder builder = HttpAsyncClientBuilder.create();
        new HttpAsyncClientConfigurer(settings).configure(builder);
        CloseableHttpAsyncClient client = builder.build();
        client.start();
        return client;
    }

    public <T> T request(HttpAsyncRequestProducer request, HttpAsyncResponseConsumer<T> responseConsumer) throws HttpRequestException {
        BasicHttpContext httpContext = new BasicHttpContext();

        Future<T> future = getClient().execute(
            decorateWithLogging(request),
            responseConsumer,
            httpContext,
            noopFutureCallback()
        );

        try {
            return future.get();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException) {
                HttpRequest contextRequest = HttpCoreContext.adapt(httpContext).getRequest();
                HttpUriRequest contextUriRequest = (HttpUriRequest) contextRequest;
                throw HttpClientUtil.toHttpRequestException(contextUriRequest, HttpClientUtil.stripUserCredentials(effectiveUri(contextUriRequest, httpContext)), (IOException) cause, documentationRegistry);
            } else {
                throw UncheckedException.throwAsUncheckedException(cause);
            }
        }
    }

    private HttpAsyncRequestProducer decorateWithLogging(HttpAsyncRequestProducer delegate) {
        return new DelegatingAsyncRequestProducer(delegate) {
            @Override
            public HttpRequest generateRequest() throws IOException, HttpException {
                HttpRequest httpRequest = super.generateRequest();
                if (LOGGER.isDebugEnabled()) {
                    if (httpRequest instanceof HttpUriRequest) {
                        HttpUriRequest httpUriRequest = (HttpUriRequest) httpRequest;
                        LOGGER.debug("Performing HTTP {}: {}", httpUriRequest.getMethod(), stripUserCredentials(httpUriRequest.getURI()));
                    }
                }
                return httpRequest;
            }
        };
    }

    private static <T> FutureCallback<T> noopFutureCallback() {
        return new FutureCallback<T>() {
            @Override
            public void completed(T result) {

            }

            @Override
            public void failed(Exception ex) {

            }

            @Override
            public void cancelled() {

            }
        };
    }

    @Override
    public synchronized void close() throws IOException {
        if (client != null) {
            client.close();
        }
    }

}
