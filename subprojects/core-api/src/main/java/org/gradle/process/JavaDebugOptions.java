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

package org.gradle.process;

import org.gradle.api.Incubating;

import java.util.Objects;

/**
 * Contains a subset of the <a href="https://docs.oracle.com/javase/8/docs/technotes/guides/troubleshoot/introclientissues005.html">Java Debug Wire Protocol</a> properties.
 *
 * @since 5.6
 */
@Incubating
public class JavaDebugOptions {

    private final int port;
    private final boolean server;
    private final boolean suspend;

    public JavaDebugOptions(int port, boolean server, boolean suspend) {
        this.port = port;
        this.server = server;
        this.suspend = suspend;
    }

    public int getPort() {
        return port;
    }

    public boolean isServer() {
        return server;
    }

    public boolean isSuspend() {
        return suspend;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JavaDebugOptions that = (JavaDebugOptions) o;
        return port == that.port &&
            server == that.server &&
            suspend == that.suspend;
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, server, suspend);
    }
}
