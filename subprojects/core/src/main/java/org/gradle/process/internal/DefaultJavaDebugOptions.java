/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.process.internal;

import org.gradle.process.JavaDebugOptions;

import java.util.Objects;

public class DefaultJavaDebugOptions implements JavaDebugOptions {

    private boolean enabled;
    private int port = 5005;
    private boolean server;
    private boolean suspend;

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isServer() {
        return server;
    }

    public void setServer(boolean server) {
        this.server = server;
    }

    public boolean isSuspend() {
        return suspend;
    }

    public void setSuspend(boolean suspend) {
        this.suspend = suspend;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DefaultJavaDebugOptions that = (DefaultJavaDebugOptions) o;
        return enabled == that.enabled &&
            port == that.port &&
            server == that.server &&
            suspend == that.suspend;
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, port, server, suspend);
    }

    public void applyTo(JavaDebugOptions options) {
        DefaultJavaDebugOptions defaultOptions = (DefaultJavaDebugOptions) options;
        defaultOptions.setEnabled(isEnabled());
        defaultOptions.setServer(isServer());
        defaultOptions.setSuspend(isSuspend());
        defaultOptions.setPort(getPort());
    }
}
