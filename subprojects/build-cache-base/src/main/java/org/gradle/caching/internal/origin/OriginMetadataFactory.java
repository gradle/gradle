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

import org.gradle.caching.internal.CacheableEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Properties;

public class OriginMetadataFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(OriginMetadataFactory.class);

    private static final String BUILD_INVOCATION_ID_KEY = "buildInvocationId";
    private static final String TYPE_KEY = "type";
    private static final String IDENTITY_KEY = "identity";
    private static final String CREATION_TIME_KEY = "creationTime";
    private static final String EXECUTION_TIME_KEY = "executionTime";
    private static final String OPERATING_SYSTEM_KEY = "operatingSystem";
    private static final String HOST_NAME_KEY = "hostName";
    private static final String USER_NAME_KEY = "userName";

    private final String userName;
    private final String operatingSystem;
    private final String currentBuildInvocationId;
    private final PropertiesConfigurator additionalProperties;
    private final HostnameLookup hostnameLookup;

    public OriginMetadataFactory(
        String userName,
        String operatingSystem,
        String currentBuildInvocationId,
        PropertiesConfigurator additionalProperties,
        HostnameLookup hostnameLookup
    ) {
        this.userName = userName;
        this.operatingSystem = operatingSystem;
        this.additionalProperties = additionalProperties;
        this.currentBuildInvocationId = currentBuildInvocationId;
        this.hostnameLookup = hostnameLookup;
    }

    public OriginWriter createWriter(CacheableEntity entry, Duration elapsedTime) {
        return outputStream -> {
            Properties properties = new Properties();
            properties.setProperty(BUILD_INVOCATION_ID_KEY, currentBuildInvocationId);
            properties.setProperty(TYPE_KEY, entry.getType().getCanonicalName());
            properties.setProperty(IDENTITY_KEY, entry.getIdentity());
            properties.setProperty(CREATION_TIME_KEY, Long.toString(System.currentTimeMillis()));
            properties.setProperty(EXECUTION_TIME_KEY, Long.toString(elapsedTime.toMillis()));
            properties.setProperty(OPERATING_SYSTEM_KEY, operatingSystem);
            properties.setProperty(HOST_NAME_KEY, hostnameLookup.getHostname());
            properties.setProperty(USER_NAME_KEY, userName);
            additionalProperties.configure(properties);
            properties.store(outputStream, "Generated origin information");
        };
    }

    public OriginReader createReader(CacheableEntity entry) {
        return inputStream -> {
            Properties properties = new Properties();
            properties.load(inputStream);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Origin for {}: {}", entry.getDisplayName(), properties);
            }

            String originBuildInvocationId = properties.getProperty(BUILD_INVOCATION_ID_KEY);
            String executionTimeAsString = properties.getProperty(EXECUTION_TIME_KEY);

            if (originBuildInvocationId == null || executionTimeAsString == null) {
                throw new IllegalStateException("Cached result format error, corrupted origin metadata");
            }

            Duration originalExecutionTime = Duration.ofMillis(Long.parseLong(executionTimeAsString));
            return new OriginMetadata(originBuildInvocationId, originalExecutionTime);
        };
    }

    public interface PropertiesConfigurator {
        void configure(Properties properties);
    }

    public interface HostnameLookup {
        String getHostname();
    }
}
