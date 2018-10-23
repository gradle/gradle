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

package org.gradle.caching.internal.origin;

import com.google.common.collect.ImmutableSet;
import org.gradle.caching.internal.CacheableEntity;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.time.Clock;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Set;

public class OriginMetadataFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginMetadataFactory.class);

    private static final String BUILD_INVOCATION_ID_KEY = "buildInvocationId";
    private static final String TYPE_KEY = "type";
    private static final String IDENTITY_KEY = "identity";
    private static final String GRADLE_VERSION_KEY = "gradleVersion";
    private static final String CREATION_TIME_KEY = "creationTime";
    private static final String EXECUTION_TIME_KEY = "executionTime";
    private static final String ROOT_PATH_KEY = "rootPath";
    private static final String OPERATING_SYSTEM_KEY = "operatingSystem";
    private static final String HOST_NAME_KEY = "hostName";
    private static final String USER_NAME_KEY = "userName";
    private static final Set<String> METADATA_KEYS = ImmutableSet.of(BUILD_INVOCATION_ID_KEY, TYPE_KEY, IDENTITY_KEY, GRADLE_VERSION_KEY, CREATION_TIME_KEY, EXECUTION_TIME_KEY, ROOT_PATH_KEY, OPERATING_SYSTEM_KEY, HOST_NAME_KEY, USER_NAME_KEY);

    private final InetAddressFactory inetAddressFactory;
    private final String userName;
    private final String operatingSystem;
    private final Clock clock;
    private final GradleVersion gradleVersion;
    private final UniqueId currentBuildInvocationId;
    private final File rootDir;

    public OriginMetadataFactory(Clock clock, InetAddressFactory inetAddressFactory, File rootDir, String userName, String operatingSystem, GradleVersion gradleVersion, UniqueId currentBuildInvocationId) {
        this.inetAddressFactory = inetAddressFactory;
        this.rootDir = rootDir;
        this.userName = userName;
        this.operatingSystem = operatingSystem;
        this.clock = clock;
        this.gradleVersion = gradleVersion;
        this.currentBuildInvocationId = currentBuildInvocationId;
    }

    public OriginWriter createWriter(CacheableEntity entry, long elapsedTime) {
        return new OriginWriter() {
            @Override
            public void execute(OutputStream outputStream) {
                // TODO: Replace this with something better
                Properties properties = new Properties();
                properties.setProperty(BUILD_INVOCATION_ID_KEY, currentBuildInvocationId.asString());
                properties.setProperty(TYPE_KEY, entry.getClass().getCanonicalName());
                properties.setProperty(IDENTITY_KEY, entry.getIdentity());
                properties.setProperty(GRADLE_VERSION_KEY, gradleVersion.getVersion());
                properties.setProperty(CREATION_TIME_KEY, Long.toString(clock.getCurrentTime()));
                properties.setProperty(EXECUTION_TIME_KEY, Long.toString(elapsedTime));
                properties.setProperty(ROOT_PATH_KEY, rootDir.getAbsolutePath());
                properties.setProperty(OPERATING_SYSTEM_KEY, operatingSystem);
                properties.setProperty(HOST_NAME_KEY, inetAddressFactory.getHostname());
                properties.setProperty(USER_NAME_KEY, userName);
                try {
                    properties.store(outputStream, "Generated origin information");
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                assert METADATA_KEYS.containsAll(properties.stringPropertyNames()) : "Update expected metadata property list";
            }
        };
    }

    public OriginReader createReader(CacheableEntity entry) {
        return new OriginReader() {
            @Override
            public OriginMetadata execute(InputStream inputStream) {
                // TODO: Replace this with something better
                Properties properties = new Properties();
                try {
                    properties.load(inputStream);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (!properties.stringPropertyNames().containsAll(METADATA_KEYS)) {
                    throw new IllegalStateException("Cached result format error, corrupted origin metadata.");
                }
                LOGGER.info("Origin for {}: {}", entry, properties);

                UniqueId originBuildInvocationId = UniqueId.from(properties.getProperty(BUILD_INVOCATION_ID_KEY));
                long originalExecutionTime = Long.parseLong(properties.getProperty(EXECUTION_TIME_KEY));
                return OriginMetadata.fromPreviousBuild(originBuildInvocationId, originalExecutionTime);
            }
        };
    }
}
