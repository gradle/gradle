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

import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.transfer.ExternalResourceUploader;

import java.io.IOException;
import java.net.URI;

public class HttpResourceUploader implements ExternalResourceUploader {

    private final HttpClientHelper http;

    public HttpResourceUploader(HttpClientHelper http) {
        this.http = http;
    }

    @Override
    public void upload(ReadableContent resource, ExternalResourceName destination) throws IOException {
        HttpPut method = new HttpPut(destination.getUri());
        final RepeatableInputStreamEntity entity = new RepeatableInputStreamEntity(resource, ContentType.APPLICATION_OCTET_STREAM);
        method.setEntity(entity);
        try (HttpClientResponse response = http.performHttpRequest(method)) {
            if (!response.wasSuccessful()) {
                URI effectiveUri = response.getEffectiveUri();
                throw new HttpErrorStatusCodeException(response.getMethod(), effectiveUri.toString(), response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase());
            }
        }
    }
}
