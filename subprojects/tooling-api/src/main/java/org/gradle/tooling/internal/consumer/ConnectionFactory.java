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
package org.gradle.tooling.internal.consumer;

import org.gradle.listener.ListenerManager;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.protocol.ConnectionVersion4;

/**
 * This is the main internal entry point for the tooling API.
 *
 * This implementation is thread-safe.
 */
public class ConnectionFactory {
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
    private final ToolingImplementationLoader toolingImplementationLoader;
    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();
    private final ListenerManager listenerManager;
    private final ProgressLoggerFactory progressLoggerFactory;

    public ConnectionFactory(ToolingImplementationLoader toolingImplementationLoader, ListenerManager listenerManager, ProgressLoggerFactory progressLoggerFactory) {
        this.toolingImplementationLoader = toolingImplementationLoader;
        this.listenerManager = listenerManager;
        this.progressLoggerFactory = progressLoggerFactory;
    }

    public ProjectConnection create(Distribution distribution, ConnectionParameters parameters) {
        ConnectionVersion4 connection = new ProgressLoggingConnection(new LazyConnection(distribution, toolingImplementationLoader), progressLoggerFactory, listenerManager);
        AsyncConnection asyncConnection = new DefaultAsyncConnection(connection, executorFactory);
        return new DefaultProjectConnection(asyncConnection, adapter, parameters);
    }
}
