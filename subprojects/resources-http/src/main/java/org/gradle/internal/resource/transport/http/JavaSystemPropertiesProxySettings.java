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

package org.gradle.internal.resource.transport.http;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class JavaSystemPropertiesProxySettings implements HttpProxySettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaSystemPropertiesProxySettings.class);

    private final HttpProxy proxy;
    private final String propertyPrefix;
    private final int defaultPort;

    public JavaSystemPropertiesProxySettings(String propertyPrefix, int defaultPort) {
        this(propertyPrefix, defaultPort,
                System.getProperty(propertyPrefix + ".proxyHost"),
                System.getProperty(propertyPrefix + ".proxyPort"),
                System.getProperty(propertyPrefix + ".proxyUser"),
                System.getProperty(propertyPrefix + ".proxyPassword"));
    }

    JavaSystemPropertiesProxySettings(String propertyPrefix, int defaultPort, String proxyHost, String proxyPortString, String proxyUser, String proxyPassword) {
        this.propertyPrefix = propertyPrefix;
        this.defaultPort = defaultPort;
        if (StringUtils.isBlank(proxyHost)) {
            this.proxy = null;
        } else {
            this.proxy = new HttpProxy(proxyHost, initProxyPort(proxyPortString), proxyUser, proxyPassword);
        }
    }

    private int initProxyPort(String proxyPortString) {
        if (StringUtils.isBlank(proxyPortString)) {
            return defaultPort;
        }
        try {
            return Integer.parseInt(proxyPortString);
        } catch (NumberFormatException e) {
            String key = propertyPrefix + ".proxyPort";
            LOGGER.warn("Invalid value for java system property '{}': '{}'. Value is not a valid number. Default port '{}' will be used.",
                key, proxyPortString, defaultPort);
            return defaultPort;
        }
    }

    @Override
    public HttpProxySettings.HttpProxy getProxy() {
        return proxy;
    }

    public String getPropertyPrefix() {
        return propertyPrefix;
    }

    public int getDefaultPort() {
        return defaultPort;
    }

    private static String getAndTrimSystemProperty(String key) {
        String value = System.getProperty(key);
        return value != null ? value.trim() : null;
    }
}
