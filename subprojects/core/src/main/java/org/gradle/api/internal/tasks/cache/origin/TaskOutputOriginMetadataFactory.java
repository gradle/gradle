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

import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.time.TimeProvider;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;

public class TaskOutputOriginMetadataFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(TaskOutputOriginMetadataFactory.class);

    private final InetAddressFactory inetAddressFactory;
    private final String userName;
    private final String operatingSystem;
    private final TimeProvider timeProvider;
    private final GradleVersion gradleVersion;
    private final File rootDir;

    public TaskOutputOriginMetadataFactory(TimeProvider timeProvider, InetAddressFactory inetAddressFactory, File rootDir, String userName, String operatingSystem, GradleVersion gradleVersion) {
        this.inetAddressFactory = inetAddressFactory;
        this.rootDir = rootDir;
        this.userName = userName;
        this.operatingSystem = operatingSystem;
        this.timeProvider = timeProvider;
        this.gradleVersion = gradleVersion;
    }

    public OriginMetadataWriter createWriter(final TaskInternal task, final long elapsedTime) {
        return new OriginMetadataWriter() {
            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
                // TODO: Replace this with something better
                Properties properties = new Properties();
                properties.setProperty("type", task.getClass().getCanonicalName());
                properties.setProperty("path", task.getPath());
                properties.setProperty("gradleVersion", gradleVersion.getVersion());
                properties.setProperty("creationTime", Long.toString(timeProvider.getCurrentTime()));
                properties.setProperty("executionTime", Long.toString(elapsedTime));
                properties.setProperty("rootPath", rootDir.getAbsolutePath());
                properties.setProperty("operatingSystem", operatingSystem);
                properties.setProperty("hostName", inetAddressFactory.getHostname());
                properties.setProperty("userName", userName);
                properties.store(outputStream, "origin metadata");
            }
        };
    }

    public OriginMetadataReader createReader(final TaskInternal task) {
        return new OriginMetadataReader() {
            @Override
            public void readFrom(InputStream inputStream) throws IOException {
                // TODO: Replace this with something better
                Properties properties = new Properties();
                properties.load(inputStream);
                LOGGER.info("Origin for {}: {}", task, properties);
            }
        };
    }
}
