/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.resource.transport.gcp.gcs;

import com.google.api.services.storage.model.StorageObject;
import org.gradle.api.resources.ResourceException;
import org.gradle.internal.resource.ExternalResourceName;
import org.gradle.internal.resource.ReadableContent;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.AbstractExternalResourceAccessor;
import org.gradle.internal.resource.transfer.ExternalResourceConnector;
import org.gradle.internal.resource.transfer.ExternalResourceReadResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.gradle.internal.resource.transport.gcp.gcs.ResourceMapper.toExternalResourceMetaData;

public class GcsResourceConnector extends AbstractExternalResourceAccessor implements ExternalResourceConnector {

    private static final Logger LOGGER = LoggerFactory.getLogger(GcsResourceConnector.class);

    private final GcsClient gcsClient;

    public GcsResourceConnector(GcsClient gcsClient) {
        this.gcsClient = gcsClient;
    }

    @Nullable
    @Override
    public List<String> list(ExternalResourceName parent) throws ResourceException {
        LOGGER.debug("Listing parent resources: {}", parent);
        return gcsClient.list(parent.getUri());
    }

    @Nullable
    @Override
    public ExternalResourceReadResponse openResource(ExternalResourceName location, boolean revalidate) throws ResourceException {
        LOGGER.debug("Attempting to get resource: {}", location);
        StorageObject gcsObject = gcsClient.getResource(location.getUri());
        if (gcsObject == null) {
            return null;
        }
        return new GcsResource(gcsClient, gcsObject, location.getUri());
    }

    @Nullable
    @Override
    public ExternalResourceMetaData getMetaData(ExternalResourceName location, boolean revalidate) throws ResourceException {
        LOGGER.debug("Attempting to get resource metadata: {}", location);
        StorageObject gcsObject = gcsClient.getResource(location.getUri());
        if (gcsObject == null) {
            return null;
        }
        return toExternalResourceMetaData(location.getUri(), gcsObject);
    }

    @Override
    public void upload(ReadableContent resource, ExternalResourceName destination) throws IOException {
        LOGGER.debug("Attempting to upload stream to: {}", destination);
        InputStream inputStream = resource.open();
        try {
            gcsClient.put(inputStream, resource.getContentLength(), destination.getUri());
        } finally {
            inputStream.close();
        }
    }
}
