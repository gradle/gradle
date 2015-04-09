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

import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Date;

public class S3Resource implements ExternalResourceReadResponse {

    private final S3Object s3Object;
    private final URI uri;

    public S3Resource(S3Object s3Object, URI uri) {
        this.s3Object = s3Object;
        this.uri = uri;
    }

    public InputStream openStream() throws IOException {
        return s3Object.getObjectContent();
    }

    public URI getURI() {
        return uri;
    }

    public long getContentLength() {
        return s3Object.getObjectMetadata().getContentLength();
    }

    public boolean isLocal() {
        return false;
    }

    public ExternalResourceMetaData getMetaData() {
        ObjectMetadata objectMetadata = s3Object.getObjectMetadata();
        Date lastModified = objectMetadata.getLastModified();
        return new DefaultExternalResourceMetaData(uri,
                lastModified.getTime(),
                getContentLength(),
                s3Object.getObjectMetadata().getContentType(),
                s3Object.getObjectMetadata().getETag(),
                null); // Passing null for sha1 - TODO - consider using the etag which is an MD5 hash of the file (when less than 5Gb)
    }

    @Override
    public void close() throws IOException {
        s3Object.close();
    }
}
