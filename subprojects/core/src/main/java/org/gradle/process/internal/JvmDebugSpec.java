/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.api.NonNullApi;
import org.gradle.process.JavaDebugOptions;

import javax.annotation.Nullable;

@NonNullApi
public interface JvmDebugSpec {

    boolean isEnabled();

    void setEnabled(boolean enabled);

    @Nullable
    String getHost();

    void setHost(@Nullable String host);

    int getPort();

    void setPort(int port);

    boolean isServer();

    void setServer(boolean server);

    boolean isSuspend();

    void setSuspend(boolean suspend);

    @NonNullApi
    class DefaultJvmDebugSpec implements JvmDebugSpec {
        private boolean enabled;
        private String host;
        private int port;
        private boolean server;
        private boolean suspend;

        public DefaultJvmDebugSpec() {
            this.enabled = false;
            this.host = null;
            this.port = 5005;
            this.server = true;
            this.suspend = true;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        @Override
        @Nullable
        public String getHost() {
            return host;
        }

        @Override
        public void setHost(@Nullable String host) {
            this.host = host;
        }

        @Override
        public int getPort() {
            return port;
        }

        @Override
        public void setPort(int port) {
            this.port = port;
        }

        @Override
        public boolean isServer() {
            return server;
        }

        @Override
        public void setServer(boolean server) {
            this.server = server;
        }

        @Override
        public boolean isSuspend() {
            return suspend;
        }

        @Override
        public void setSuspend(boolean suspend) {
            this.suspend = suspend;
        }
    }

    @NonNullApi
    class JavaDebugOptionsBackedSpec implements JvmDebugSpec {
        private final JavaDebugOptions delegate;

        public JavaDebugOptionsBackedSpec(JavaDebugOptions delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isEnabled() {
            return delegate.getEnabled().get();
        }

        @Override
        public void setEnabled(boolean enabled) {
            delegate.getEnabled().set(enabled);
        }

        @Override
        @Nullable
        public String getHost() {
            return delegate.getHost().getOrNull();
        }

        @Override
        public void setHost(@Nullable String host) {
            delegate.getHost().set(host);
        }

        @Override
        public int getPort() {
            return delegate.getPort().get();
        }

        @Override
        public void setPort(int port) {
            delegate.getPort().set(port);
        }

        @Override
        public boolean isServer() {
            return delegate.getServer().get();
        }

        @Override
        public void setServer(boolean server) {
            delegate.getServer().set(server);
        }

        @Override
        public boolean isSuspend() {
            return delegate.getSuspend().get();
        }

        @Override
        public void setSuspend(boolean suspend) {
            delegate.getSuspend().set(suspend);
        }
    }
}
