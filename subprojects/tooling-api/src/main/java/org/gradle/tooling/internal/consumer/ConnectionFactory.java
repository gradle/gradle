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

import org.gradle.messaging.concurrent.DefaultExecutorFactory;
import org.gradle.tooling.ProjectConnection;

/**
 * This is the main internal entry point for the tooling API.
 *
 * This implementation is thread-safe.
 */
public class ConnectionFactory {
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter();
    private final ToolingImplementationLoader toolingImplementationLoader;
    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();

    public ConnectionFactory() {
        this(new CachingToolingImplementationLoader(new DefaultToolingImplementationLoader()));
    }

    ConnectionFactory(ToolingImplementationLoader toolingImplementationLoader) {
        this.toolingImplementationLoader = toolingImplementationLoader;
    }

    public ProjectConnection create(Distribution distribution, ConnectionParameters parameters) {
        AsyncConnection connection = new DefaultAsyncConnection(new ProgressLoggingConnection(new LazyConnection(distribution, toolingImplementationLoader)), executorFactory);
        return new DefaultProjectConnection(connection, adapter, parameters);
    }
}
