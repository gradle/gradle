/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins.jetty;

/**
 * Convention properties and methods added by the {@link org.gradle.api.plugins.jetty.JettyPlugin}.
 */
public class JettyPluginConvention {
    private Integer stopPort;
    private String stopKey;
    private Integer httpPort = 8080;

    /**
     * Returns the TCP port for Jetty to listen on for incoming HTTP requests.
     */
    public Integer getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(Integer httpPort) {
        this.httpPort = httpPort;
    }

    /**
     * Returns the TCP port for Jetty to listen on for stop requests.
     */
    public Integer getStopPort() {
        return stopPort;
    }

    public void setStopPort(Integer stopPort) {
        this.stopPort = stopPort;
    }

    /**
     * Returns the key to use to stop Jetty.
     */
    public String getStopKey() {
        return stopKey;
    }

    public void setStopKey(String stopKey) {
        this.stopKey = stopKey;
    }
}
