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

import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.S3Object;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Factory;
import org.gradle.internal.hash.HashValue;
import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.metadata.DefaultExternalResourceMetaData;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class S3ResourceConnector implements ExternalResourceConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(S3ResourceConnector.class);
    private final S3Client s3Client;

    public S3ResourceConnector(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    public List<String> list(URI parent) throws IOException {
        LOGGER.debug("Listing parent resources: {}", parent);
        List<String> list = s3Client.list(parent);
        return list;
    }

    public ExternalResource getResource(URI location) throws IOException {
        LOGGER.debug("Attempting to get resource: {}", location);
        try {
            S3Object s3Object = s3Client.getResource(location);
            if (s3Object == null) {
                return null;
            }
            return new S3Resource(s3Object, location);
        } catch (S3Exception s3x) {
            throw new UncheckedIOException(s3x.getMessage(), s3x);
        }
    }

    public HashValue getResourceSha1(URI location) {
        LOGGER.debug("Attempting to get resource sha1: {}", location);
        try {
            S3Object resource = s3Client.getResource(new URI(location.toString() + ".sha1"));
            if (resource != null) {
                InputStream objectContent = resource.getObjectContent();
                String sha = IOUtils.toString(objectContent);
                return HashValue.parse(sha);
            }
        } catch (AmazonS3Exception e) {
            LOGGER.error("Could not get contents of resource sha", e);
        } catch (IOException e) {
            LOGGER.error("Could not get contents of resource sha", e);
        } catch (URISyntaxException e) {
            LOGGER.error("Could not create URI for sha resource", e);
        }
        return null;
    }

    public ExternalResourceMetaData getMetaData(URI location) throws IOException {
        LOGGER.debug("Attempting to get resource metadata: {}", location);
        try {
            S3Object s3Object = s3Client.getMetaData(location);
            if (s3Object == null) {
                return null;
            }
            ObjectMetadata objectMetadata = s3Object.getObjectMetadata();
            return new DefaultExternalResourceMetaData(location,
                    objectMetadata.getLastModified().getTime(),
                    objectMetadata.getContentLength(),
                    objectMetadata.getETag(),
                    null); // Passing null for sha1 - TODO - consider using the etag which is an MD5 hash of the file (when less than 5Gb)

        } catch (S3Exception s3x) {
            throw new UncheckedIOException(s3x.getMessage(), s3x);
        }
    }

    public void upload(Factory<InputStream> sourceFactory, Long contentLength, URI destination) throws IOException {
        LOGGER.debug("Attempting to upload stream to : {}", destination);
        InputStream inputStream = sourceFactory.create();
        s3Client.put(inputStream, contentLength, destination);
        inputStream.close();
    }
}
