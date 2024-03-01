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

import org.gradle.internal.hash.HashCode;

import java.time.Duration;
import java.util.Properties;

public class OriginMetadataFactory {

    private static final String BUILD_INVOCATION_ID_KEY = "buildInvocationId";
    private static final String TYPE_KEY = "type";
    private static final String IDENTITY_KEY = "identity";
    private static final String CACHE_KEY = "buildCacheKey";
    private static final String CREATION_TIME_KEY = "creationTime";
    private static final String EXECUTION_TIME_KEY = "executionTime";

    private final String currentBuildInvocationId;
    private final PropertiesConfigurator additionalProperties;

    public OriginMetadataFactory(
        String currentBuildInvocationId,
        PropertiesConfigurator additionalProperties
    ) {
        this.additionalProperties = additionalProperties;
        this.currentBuildInvocationId = currentBuildInvocationId;
    }

    public OriginWriter createWriter(String identity, Class<?> workType, HashCode buildCacheKey, Duration elapsedTime) {
        return outputStream -> {
            Properties properties = new Properties();
            properties.setProperty(BUILD_INVOCATION_ID_KEY, currentBuildInvocationId);
            properties.setProperty(TYPE_KEY, workType.getCanonicalName());
            properties.setProperty(IDENTITY_KEY, identity);
            properties.setProperty(CACHE_KEY, buildCacheKey.toString());
            properties.setProperty(CREATION_TIME_KEY, Long.toString(System.currentTimeMillis()));
            properties.setProperty(EXECUTION_TIME_KEY, Long.toString(elapsedTime.toMillis()));
            additionalProperties.configure(properties);
            properties.store(outputStream, "Generated origin information");
        };
    }

    public OriginReader createReader() {
        return inputStream -> {
            Properties properties = new Properties();
            properties.load(inputStream);

            String originBuildInvocationId = properties.getProperty(BUILD_INVOCATION_ID_KEY);
            String originBuildCacheKey = properties.getProperty(CACHE_KEY);
            String executionTimeAsString = properties.getProperty(EXECUTION_TIME_KEY);

            if (originBuildInvocationId == null || executionTimeAsString == null) {
                throw new IllegalStateException("Cached result format error, corrupted origin metadata");
            }

            Duration originalExecutionTime = Duration.ofMillis(Long.parseLong(executionTimeAsString));
            return new OriginMetadata(
                originBuildInvocationId,
                HashCode.fromString(originBuildCacheKey),
                originalExecutionTime);
        };
    }

    public interface PropertiesConfigurator {
        void configure(Properties properties);
    }
}
