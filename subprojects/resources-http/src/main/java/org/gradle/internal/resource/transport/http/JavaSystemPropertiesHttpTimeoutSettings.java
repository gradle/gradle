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

package org.gradle.internal.resource.transport.http;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaSystemPropertiesHttpTimeoutSettings implements HttpTimeoutSettings {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaSystemPropertiesHttpTimeoutSettings.class);
    public static final String CONNECTION_TIMEOUT_SYSTEM_PROPERTY = "http.connectionTimeout";
    public static final String SOCKET_TIMEOUT_SYSTEM_PROPERTY = "http.socketTimeout";
    public static final int DEFAULT_CONNECTION_TIMEOUT = 10000;
    public static final int DEFAULT_SOCKET_TIMEOUT = 30000;
    private final int connectionTimeout;
    private final int socketTimeout;

    public JavaSystemPropertiesHttpTimeoutSettings() {
        this.connectionTimeout = initTimeout(CONNECTION_TIMEOUT_SYSTEM_PROPERTY, DEFAULT_CONNECTION_TIMEOUT);
        this.socketTimeout = initTimeout(SOCKET_TIMEOUT_SYSTEM_PROPERTY, DEFAULT_SOCKET_TIMEOUT);
    }

    @Override
    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    @Override
    public int getSocketTimeout() {
        return socketTimeout;
    }

    private int initTimeout(String propertyName, int defaultValue) {
        String systemProperty = System.getProperty(propertyName);

        if (!StringUtils.isBlank(systemProperty)) {
            try {
                return Integer.parseInt(systemProperty);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid value for java system property '{}': {}. Default timeout '{}' will be used.",
                    propertyName, systemProperty, defaultValue);
            }
        }

        return defaultValue;
    }
}
