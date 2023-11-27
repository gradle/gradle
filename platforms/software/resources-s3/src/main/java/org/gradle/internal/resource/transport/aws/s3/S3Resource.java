/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.internal.resource.transport.aws.s3;

import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;

public class S3Resource implements ExternalResourceReadResponse {

    private final S3Client.GetResourceResponse getResourceResponse;
    private final URI uri;

    public S3Resource(S3Client.GetResourceResponse getResourceResponse, URI uri) {
        this.getResourceResponse = getResourceResponse;
        this.uri = uri;
    }

    @Override
    public InputStream openStream() throws IOException {
        return getResourceResponse.getResponseInputStream();
    }

    public URI getURI() {
        return uri;
    }

    public long getContentLength() {
        return getResourceResponse.getResponseInputStream().response().contentLength();
    }

    public boolean isLocal() {
        return false;
    }

    @Override
    public ExternalResourceMetaData getMetaData() {
        GetObjectResponse objectMetadata = getResourceResponse.getResponseInputStream().response();
        Instant lastModified = objectMetadata.lastModified();
        return new DefaultExternalResourceMetaData(uri,
                lastModified == null ? 0 : lastModified.toEpochMilli(),
                getContentLength(),
                getResourceResponse.getResponseInputStream().response().contentType(),
                getResourceResponse.getResponseInputStream().response().eTag(),
                null); // Passing null for sha1 - TODO - consider using the etag which is an MD5 hash of the file (when less than 5Gb)
    }

    @Override
    public void close() throws IOException {
        getResourceResponse.close();
    }
}
