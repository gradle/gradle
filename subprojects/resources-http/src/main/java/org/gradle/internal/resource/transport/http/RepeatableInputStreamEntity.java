/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.internal.resource.transport.http;

import org.apache.commons.io.IOUtils;
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.ContentType;
import org.gradle.internal.Factory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RepeatableInputStreamEntity extends AbstractHttpEntity {
    private final Factory<InputStream> source;
    private final Long contentLength;

    public RepeatableInputStreamEntity(Factory<InputStream> source, Long contentLength, ContentType contentType) {
        super();
        this.source = source;
        this.contentLength = contentLength;
        if (contentType != null) {
            setContentType(contentType.toString());
        }
    }

    public boolean isRepeatable() {
        return true;
    }

    public long getContentLength() {
        return contentLength;
    }

    public InputStream getContent() throws IOException, IllegalStateException {
        return source.create();
    }

    public void writeTo(OutputStream outstream) throws IOException {
        IOUtils.copyLarge(getContent(), outstream);
    }

    public boolean isStreaming() {
        return true;
    }
}
