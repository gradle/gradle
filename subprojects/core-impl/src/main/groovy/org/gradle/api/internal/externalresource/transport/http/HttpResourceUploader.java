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

package org.gradle.api.internal.externalresource.transport.http;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.gradle.api.internal.externalresource.transfer.ExternalResourceUploader;
import org.gradle.internal.Factory;

import java.io.IOException;
import java.io.InputStream;

public class HttpResourceUploader implements ExternalResourceUploader {

    private final HttpClientHelper http;

    public HttpResourceUploader(HttpClientHelper http) {
        this.http = http;
    }

    public void upload(Factory<InputStream> source, Long contentLength, String destination) throws IOException {
        HttpPut method = new HttpPut(destination);
        final RepeatableInputStreamEntity entity = new RepeatableInputStreamEntity(source, contentLength, ContentType.APPLICATION_OCTET_STREAM);
        method.setEntity(entity);
        HttpResponse response = http.performHttpRequest(method);
        EntityUtils.consume(response.getEntity());
        if (!http.wasSuccessful(response)) {
            throw new IOException(String.format("Could not PUT '%s'. Received status code %s from server: %s",
                    destination, response.getStatusLine().getStatusCode(), response.getStatusLine().getReasonPhrase()));
        }

    }
}
