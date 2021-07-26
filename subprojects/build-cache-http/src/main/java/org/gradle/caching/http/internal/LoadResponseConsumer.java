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

package org.gradle.caching.http.internal;

import org.apache.http.HttpException;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentDecoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.client.methods.ZeroCopyConsumer;
import org.apache.http.nio.protocol.HttpAsyncResponseConsumer;
import org.apache.http.protocol.HttpContext;
import org.gradle.caching.http.internal.httpclient.BodyIgnoringResponseConsumer;
import org.gradle.caching.internal.BuildCacheEntryInternal;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

class LoadResponseConsumer implements HttpAsyncResponseConsumer<HttpResponse> {

    private final AtomicBoolean completed = new AtomicBoolean();
    private final BuildCacheEntryInternal entry;

    private volatile Exception exception;
    private volatile HttpAsyncResponseConsumer<?> delegate;
    private volatile HttpResponse response;

    public LoadResponseConsumer(BuildCacheEntryInternal entry) {
        this.entry = entry;
    }

    @Override
    public void responseReceived(HttpResponse response) throws IOException, HttpException {
        this.response = response;
        this.delegate = HttpBuildCacheService.isHttpSuccess(response.getStatusLine().getStatusCode())
            ? createFileWritingDelegate()
            : new BodyIgnoringResponseConsumer();

        delegate.responseReceived(response);
    }

    private HttpAsyncResponseConsumer<?> createFileWritingDelegate() throws java.io.FileNotFoundException {
        entry.markDownloading();
        return new ZeroCopyConsumer<Void>(entry.getFile()) {
            @Override
            protected Void process(HttpResponse response, File file, ContentType contentType) {
                return null;
            }

        };
    }

    @Override
    public void consumeContent(ContentDecoder decoder, IOControl ioctrl) throws IOException {
        if (delegate != null) {
            delegate.consumeContent(decoder, ioctrl);
        }
    }

    @Override
    public void responseCompleted(HttpContext context) {
        if (delegate != null) {
            delegate.responseCompleted(context);
        }
    }

    @Override
    public Exception getException() {
        if (delegate == null) {
            return this.exception;
        } else {
            return delegate.getException();
        }
    }

    @Override
    public HttpResponse getResult() {
        return response;
    }

    @Override
    public boolean isDone() {
        return delegate == null || delegate.isDone();
    }

    @Override
    public final boolean cancel() {
        if (this.completed.compareAndSet(false, true)) {
            if (delegate == null) {
                return true;
            } else {
                return delegate.cancel();
            }
        } else {
            return false;
        }
    }

    @Override
    public final void failed(final Exception ex) {
        if (this.completed.compareAndSet(false, true)) {
            if (delegate == null) {
                this.exception = ex;
            } else {
                delegate.failed(exception);
            }
        }
    }

    @Override
    public final void close() throws IOException {
        if (this.completed.compareAndSet(false, true)) {
            if (delegate != null) {
                delegate.close();
            }
        }
    }
}
