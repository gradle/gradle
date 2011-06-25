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

import org.gradle.messaging.concurrent.ExecutorFactory;
import org.gradle.messaging.concurrent.StoppableExecutor;
import org.gradle.tooling.internal.protocol.*;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Adapts a {@link ConnectionVersion4} to an {@link AsyncConnection}.
 */
class DefaultAsyncConnection implements AsyncConnection {
    private final ConnectionVersion4 connection;
    private final StoppableExecutor executor;
    private final AtomicBoolean closed = new AtomicBoolean();

    public DefaultAsyncConnection(ConnectionVersion4 connection, ExecutorFactory executorFactory) {
        this.connection = connection;
        executor = executorFactory.create("Connection worker");
    }

    public String getDisplayName() {
        return connection.getMetaData().getDisplayName();
    }

    public void executeBuild(final BuildParametersVersion1 buildParameters, final BuildOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super Void> handler) throws IllegalStateException {
        runLater(handler, new ConnectionAction<Void>() {
            public Void run() {
                connection.executeBuild(buildParameters, operationParameters);
                return null;
            }
        });
    }

    public void getModel(final Class<? extends ProjectVersion3> type, final BuildOperationParametersVersion1 operationParameters, ResultHandlerVersion1<? super ProjectVersion3> handler) throws UnsupportedOperationException, IllegalStateException {
        runLater(handler, new ConnectionAction<ProjectVersion3>() {
            public ProjectVersion3 run() {
                return connection.getModel(type, operationParameters);
            }
        });
    }

    public void stop() {
        closed.set(true);
        executor.stop();
        connection.stop();
    }

    private <T> void runLater(final ResultHandlerVersion1<? super T> handler, final ConnectionAction<T> action) {
        onStartOperation();

        executor.execute(new Runnable() {
            public void run() {
                T result;
                try {
                    result = action.run();
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

    private interface ConnectionAction<T> {
        T run();
    }
}
