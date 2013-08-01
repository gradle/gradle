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
package org.gradle.tooling.internal.consumer.async;

import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.StoppableExecutor;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;
import org.gradle.tooling.internal.consumer.versioning.VersionDetails;
import org.gradle.tooling.internal.protocol.ResultHandlerVersion1;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapts a {@link ConsumerConnection} to an {@link AsyncConnection}.
 */
public class DefaultAsyncConnection implements AsyncConnection {
    private final ConsumerConnection connection;
    private final StoppableExecutor executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultAsyncConnection(ConsumerConnection connection, ExecutorFactory executorFactory) {
        this.connection = connection;
        executor = executorFactory.create("Connection worker");
    }

    public String getDisplayName() {
        return connection.getDisplayName();
    }

    public VersionDetails getVersionDetails() {
        return connection.getVersionDetails();
    }

    public void stop() {
        closed.set(true);
        executor.stop();
        connection.stop();
    }

    public <T> void run(final AsyncConnection.ConnectionAction<? extends T> action, final ResultHandlerVersion1<? super T> handler) {
        onStartOperation();

        executor.execute(new Runnable() {
            public void run() {
                T result;
                try {
                    result = action.run(connection);
                } catch (Throwable t) {
                    handler.onFailure(t);
                    return;
                }
                handler.onComplete(result);
            }
        });
    }

    private void onStartOperation() {
        if (closed.get()) {
            throw new IllegalStateException("This connection has been closed.");
        }
    }
}
