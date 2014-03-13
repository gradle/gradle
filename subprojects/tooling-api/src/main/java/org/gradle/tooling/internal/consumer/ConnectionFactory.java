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
import org.gradle.tooling.internal.consumer.async.AsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.async.DefaultAsyncConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.LazyConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.LoggingInitializerConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.connection.ProgressLoggingConsumerActionExecutor;
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader;

public class ConnectionFactory {
    private final ToolingImplementationLoader toolingImplementationLoader;
    private final DefaultExecutorFactory executorFactory = new DefaultExecutorFactory();

    public ConnectionFactory(ToolingImplementationLoader toolingImplementationLoader) {
        this.toolingImplementationLoader = toolingImplementationLoader;
    }

    public ProjectConnection create(Distribution distribution, ConnectionParameters parameters) {
        SynchronizedLogging synchronizedLogging = new SynchronizedLogging();
        ConsumerActionExecutor lazyConnection = new LazyConsumerActionExecutor(distribution, toolingImplementationLoader, synchronizedLogging, parameters);
        ConsumerActionExecutor progressLoggingConnection = new ProgressLoggingConsumerActionExecutor(lazyConnection, synchronizedLogging);
        ConsumerActionExecutor initializingConnection = new LoggingInitializerConsumerActionExecutor(progressLoggingConnection, synchronizedLogging);
        AsyncConsumerActionExecutor asyncConnection = new DefaultAsyncConsumerActionExecutor(initializingConnection, executorFactory);
        return new DefaultProjectConnection(asyncConnection, parameters);
    }

    ToolingImplementationLoader getToolingImplementationLoader() {
        return toolingImplementationLoader;
    }
}
