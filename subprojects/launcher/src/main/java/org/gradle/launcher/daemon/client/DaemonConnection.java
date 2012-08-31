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
package org.gradle.launcher.daemon.client;

import org.gradle.api.Nullable;
import org.gradle.launcher.daemon.diagnostics.DaemonDiagnostics;
import org.gradle.messaging.remote.internal.Connection;

/**
 * A simple wrapper for the connection to a daemon plus its password.
 */
public class DaemonConnection {

    private final Connection<Object> connection;
    private DaemonDiagnostics diagnostics;

    public DaemonConnection(Connection<Object> connection, DaemonDiagnostics diagnostics) {
        this.connection = connection;
        this.diagnostics = diagnostics;
    }

    public Connection<Object> getConnection() {
        return this.connection;
    }

    /**
     * @return diagnostics. Can be null - it means we don't have process diagnostics.
     */
    @Nullable
    public DaemonDiagnostics getDaemonDiagnostics() {
        return diagnostics;
    }
}