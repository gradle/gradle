/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.cache.origin;

import org.gradle.api.internal.tasks.cache.OriginMetadata;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Properties;

public class DefaultOriginMetadataWriter implements OriginMetadataWriter {
    @Override
    public void writeTo(OriginMetadata originMetadata, OutputStream outputStream) throws IOException {
        // TODO: Replace this with something better
        Properties properties = new Properties();
        properties.setProperty("type", originMetadata.getType());
        properties.setProperty("path", originMetadata.getPath());
        properties.setProperty("gradleVersion", originMetadata.getGradleVersion());
        properties.setProperty("creationTime", Long.toString(originMetadata.getCreationTime()));
        properties.setProperty("executionTime", Long.toString(originMetadata.getExecutionTime()));
        properties.setProperty("rootPath", originMetadata.getRootPath());
        properties.setProperty("operatingSystem", originMetadata.getOperatingSystem());
        properties.setProperty("hostName", originMetadata.getHostName());
        properties.setProperty("userName", originMetadata.getUserName());
        properties.store(outputStream, "origin metadata");
    }
}
