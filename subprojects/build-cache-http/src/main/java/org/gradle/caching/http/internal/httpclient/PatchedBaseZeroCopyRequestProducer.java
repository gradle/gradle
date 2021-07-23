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

import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.utils.URIUtils;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.ContentEncoder;
import org.apache.http.nio.ContentEncoderChannel;
import org.apache.http.nio.FileContentEncoder;
import org.apache.http.nio.IOControl;
import org.apache.http.nio.protocol.HttpAsyncRequestProducer;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.Args;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.URI;
import java.nio.channels.FileChannel;

abstract public class PatchedBaseZeroCopyRequestProducer implements HttpAsyncRequestProducer {

    private final URI requestURI;
    private final File file;
    private final ContentType contentType;

    private RandomAccessFile accessfile;
    private long idx = -1;

    protected PatchedBaseZeroCopyRequestProducer(
        final URI requestURI,
        final File file, final ContentType contentType
    ) {
        super();
        Args.notNull(requestURI, "Request URI");
        Args.notNull(file, "Source file");
        this.requestURI = requestURI;
        this.file = file;
        this.contentType = contentType;
    }

    private void closeChannel() throws IOException {
        if (this.accessfile != null) {
            this.accessfile.close();
            this.accessfile = null;
        }
    }

    protected abstract HttpEntityEnclosingRequest createRequest(final URI requestURI, final HttpEntity entity);

    @Override
    public HttpRequest generateRequest() {
        final BasicHttpEntity entity = new BasicHttpEntity();
        entity.setChunked(false);
        entity.setContentLength(this.file.length());
        if (this.contentType != null) {
            entity.setContentType(this.contentType.toString());
        }
        return createRequest(this.requestURI, entity);
    }

    @Override
    public synchronized HttpHost getTarget() {
        return URIUtils.extractHost(this.requestURI);
    }

    @Override
    public synchronized void produceContent(
        final ContentEncoder encoder, final IOControl ioctrl
    ) throws IOException {
        if (this.accessfile == null) {
            this.accessfile = new RandomAccessFile(file, "r");
            this.idx = 0;
        }
        final long transferred;
        FileChannel channel = this.accessfile.getChannel();
        if (encoder instanceof FileContentEncoder) {
            transferred = ((FileContentEncoder) encoder).transfer(
                channel, this.idx, Integer.MAX_VALUE);
        } else {
            transferred = channel.transferTo(
                this.idx, Integer.MAX_VALUE, new ContentEncoderChannel(encoder));
        }
        if (transferred > 0) {
            this.idx += transferred;
        }

        if (this.idx >= channel.size()) {
            encoder.complete();
            closeChannel();
        }
    }

    @Override
    public void requestCompleted(final HttpContext context) {
    }

    @Override
    public void failed(final Exception ex) {
    }

    @Override
    public boolean isRepeatable() {
        return true;
    }

    @Override
    public synchronized void resetRequest() throws IOException {
        closeChannel();
    }

    @Override
    public synchronized void close() throws IOException {
        try {
            closeChannel();
        } catch (final IOException ignore) {
            // ignore
        }
    }

}
