/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts.repositories.transport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class JavaSystemPropertiesHttpProxySettings implements HttpProxySettings {
    private static final Logger LOGGER = LoggerFactory.getLogger(JavaSystemPropertiesHttpProxySettings.class);
    private static final int DEFAULT_PROXY_PORT = 80;

    private final String proxyHost;
    private final int proxyPort;
    private final List<String> nonProxyHosts;

    public JavaSystemPropertiesHttpProxySettings() {
        this(System.getProperty("http.proxyHost"), System.getProperty("http.proxyPort"), System.getProperty("http.nonProxyHosts"));
    }

    JavaSystemPropertiesHttpProxySettings(String proxyHost, String proxyPortString, String nonProxyHostsString) {
        this.proxyHost = proxyHost;
        this.proxyPort = initProxyPort(proxyPortString);
        this.nonProxyHosts = initNonProxyHosts(nonProxyHostsString);
    }

    private int initProxyPort(String proxyPortString) {
        if (proxyPortString == null) {
            return DEFAULT_PROXY_PORT;
        }
        
        try {
            return Integer.parseInt(proxyPortString);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid value for java system property 'http.proxyPort': {}. Default port '{}' will be used.", System.getProperty("http.proxyPort"), DEFAULT_PROXY_PORT);
            return DEFAULT_PROXY_PORT;
        }
    }

    private List<String> initNonProxyHosts(String nonProxyHostsString) {
        if (nonProxyHostsString == null) {
            return Collections.emptyList();
        }
        LOGGER.debug("Found java system property 'http.nonProxyHosts': {}. Will ignore proxy settings for these hosts.", nonProxyHostsString);
        return Arrays.asList(nonProxyHostsString.split("\\|"));
    }

    public boolean isProxyConfigured(String host) {
        return proxyHost != null && !nonProxyHosts.contains(host);
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }
}
