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
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;

import java.io.IOException;

class DelegatingAsyncRequestProducer implements HttpAsyncRequestProducer {

    private final HttpAsyncRequestProducer delegate;

    DelegatingAsyncRequestProducer(HttpAsyncRequestProducer delegate) {
        this.delegate = delegate;
    }

    @Override
    public HttpHost getTarget() {
        return delegate.getTarget();
    }

    @Override
    public HttpRequest generateRequest() throws IOException, HttpException {
        return delegate.generateRequest();
    }

    @Override
    public void produceContent(ContentEncoder encoder, IOControl ioctrl) throws IOException {
        delegate.produceContent(encoder, ioctrl);
    }

    @Override
    public void requestCompleted(HttpContext context) {
        delegate.requestCompleted(context);
    }

    @Override
    public void failed(Exception ex) {
        delegate.failed(ex);
    }

    @Override
    public boolean isRepeatable() {
        return delegate.isRepeatable();
    }

    @Override
    public void resetRequest() throws IOException {
        delegate.resetRequest();
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
