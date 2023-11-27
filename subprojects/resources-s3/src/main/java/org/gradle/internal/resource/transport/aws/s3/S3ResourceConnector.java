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


import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class S3ResourceConnector extends AbstractExternalResourceAccessor implements ExternalResourceConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ResourceConnector.class);
    private final S3Client s3Client;

    public S3ResourceConnector(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public List<String> list(ExternalResourceName parent) {
        LOGGER.debug("Listing parent resources: {}", parent);
        return s3Client.listDirectChildren(parent.getUri());
    }

    @Override
    public ExternalResourceReadResponse openResource(ExternalResourceName location, boolean revalidate) {
        LOGGER.debug("Attempting to get resource: {}", location);
        S3Client.GetResourceResponse resourceItems = s3Client.getResource(location.getUri());
        if (resourceItems == null) {
            return null;
        }
        return new S3Resource(resourceItems, location.getUri());
    }

    @Override
    public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) {
        LOGGER.debug("Attempting to get resource metadata: {}", location);
        HeadObjectResponse headObjectResponse = s3Client.getMetaData(location.getUri());
        if (headObjectResponse == null) {
            return null;
        }
        return new DefaultExternalResourceMetaData(location.getUri(),
            headObjectResponse.lastModified().toEpochMilli(),
            headObjectResponse.contentLength(),
            headObjectResponse.contentType(),
            headObjectResponse.eTag(),
            null); // Passing null for sha1 - TODO - consider using the etag which is an MD5 hash of the file (when less than 5Gb)
    }

    @Override
    public void upload(ReadableContent resource, ExternalResourceName destination) throws IOException {
        LOGGER.debug("Attempting to upload stream to : {}", destination);
        InputStream inputStream = resource.open();
        try {
            s3Client.put(inputStream, resource.getContentLength(), destination.getUri());
        } finally {
            inputStream.close();
        }
    }
}
