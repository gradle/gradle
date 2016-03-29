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

package org.gradle.internal.resource.transport;


import org.gradle.internal.resource.ExternalResource;
import org.gradle.internal.resource.local.LocalResource;
import org.gradle.internal.resource.metadata.ExternalResourceMetaData;
import org.gradle.internal.resource.transfer.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.util.List;

public class DefaultExternalResourceRepository implements ExternalResourceRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExternalResourceRepository.class);
    private final String name;
    private final ExternalResourceAccessor accessor;
    private final ExternalResourceUploader uploader;
    private final ExternalResourceLister lister;
    private final ExternalResourceAccessor loggingAccessor;
    private final ExternalResourceUploader loggingUploader;

    public DefaultExternalResourceRepository(String name,
                                             ExternalResourceAccessor accessor,
                                             ExternalResourceUploader uploader,
                                             ExternalResourceLister lister,
                                             ExternalResourceAccessor loggingAccessor,
                                             ExternalResourceUploader loggingUploader) {
        this.name = name;
        this.accessor = accessor;
        this.uploader = uploader;
        this.lister = lister;
        this.loggingAccessor = loggingAccessor;
        this.loggingUploader = loggingUploader;
    }

    @Override
    public ExternalResourceRepository withProgressLogging() {
        if (loggingAccessor == accessor && loggingUploader == uploader) {
            return this;
        }
        return new DefaultExternalResourceRepository(name, loggingAccessor, loggingUploader, lister, loggingAccessor, loggingUploader);
    }

    public ExternalResource getResource(URI source, boolean revalidate) {
        ExternalResourceReadResponse response = accessor.openResource(source, revalidate);
        return response == null ? null : new DefaultExternalResource(source, response);
    }

    public ExternalResourceMetaData getResourceMetaData(URI source, boolean revalidate) {
        return accessor.getMetaData(source, revalidate);
    }

    public void put(LocalResource source, URI destination) throws IOException {
        LOGGER.debug("Attempting to put resource {}.", destination);
        uploader.upload(source, destination);
    }

    public List<String> list(URI parent) {
        return lister.list(parent);
    }

    public String toString() {
        return name;
    }

}
