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

import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.async.AsyncConnection;
import org.gradle.tooling.internal.consumer.async.DefaultAsyncConnection;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.connection.LazyConnection;
import org.gradle.tooling.internal.consumer.connection.LoggingInitializerConnection;
import org.gradle.tooling.internal.consumer.connection.ProgressLoggingConnection;
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader;
import org.gradle.tooling.internal.consumer.protocoladapter.ConsumerTargetTypeProvider;
import org.gradle.tooling.internal.consumer.protocoladapter.ProtocolToModelAdapter;

public class ConnectionFactory {
    private final ProtocolToModelAdapter adapter = new ProtocolToModelAdapter(new ConsumerTargetTypeProvider());
    private final ToolingImplementationLoader toolingImplementationLoader;
    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();

    public ConnectionFactory(ToolingImplementationLoader toolingImplementationLoader) {
        this.toolingImplementationLoader = toolingImplementationLoader;
    }

    public ProjectConnection create(Distribution distribution, ConnectionParameters parameters) {
        SynchronizedLogging synchronizedLogging = new SynchronizedLogging();
        ConsumerConnection lazyConnection = new LazyConnection(distribution, toolingImplementationLoader, synchronizedLogging, parameters.getVerboseLogging());
        ConsumerConnection progressLoggingConnection = new ProgressLoggingConnection(lazyConnection, synchronizedLogging);
        ConsumerConnection initializingConnection = new LoggingInitializerConnection(progressLoggingConnection, synchronizedLogging);
        AsyncConnection asyncConnection = new DefaultAsyncConnection(initializingConnection, executorFactory);
        return new DefaultProjectConnection(asyncConnection, adapter, parameters);
    }

    ToolingImplementationLoader getToolingImplementationLoader() {
        return toolingImplementationLoader;
    }
}
