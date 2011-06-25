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

import org.gradle.tooling.internal.protocol.*;
import org.gradle.util.UncheckedException;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A {@link ConnectionVersion4} implementation which creates the actual connection implementation on demand.
 */
public class LazyConnection implements ConnectionVersion4 {
    private final Distribution distribution;
    private final ToolingImplementationLoader implementationLoader;
    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private Set<Thread> executing = new HashSet<Thread>();
    private boolean stopped;
    private ConnectionVersion4 connection;

    public LazyConnection(Distribution distribution, ToolingImplementationLoader implementationLoader) {
        this.distribution = distribution;
        this.implementationLoader = implementationLoader;
    }

    public void stop() {
        ConnectionVersion4 connection = null;
        lock.lock();
        try {
            stopped = true;
            while (!executing.isEmpty()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.asUncheckedException(e);
                }
            }
            connection = this.connection;
            this.connection = null;
        } finally {
            lock.unlock();
        }
        if (connection != null) {
            connection.stop();
        }
    }

    public ConnectionMetaDataVersion1 getMetaData() {
        return new ConnectionMetaDataVersion1() {
            public String getVersion() {
                throw new UnsupportedOperationException();
            }

            public String getDisplayName() {
                return distribution.getDisplayName();
            }
        };
    }

    public void executeBuild(final BuildParametersVersion1 buildParameters, final BuildOperationParametersVersion1 operationParameters) {
        withConnection(new ConnectionAction<Object>() {
            public Object run(ConnectionVersion4 connection) {
                connection.executeBuild(buildParameters, operationParameters);
                return null;
            }
        });
    }

    public ProjectVersion3 getModel(final Class<? extends ProjectVersion3> type, final BuildOperationParametersVersion1 operationParameters) {
        return withConnection(new ConnectionAction<ProjectVersion3>() {
            public ProjectVersion3 run(ConnectionVersion4 connection) {
                return connection.getModel(type, operationParameters);
            }
        });
    }

    private <T> T withConnection(ConnectionAction<T> action) {
        try {
            ConnectionVersion4 connection = onStartAction();
            return action.run(connection);
        } finally {
            onEndAction();
        }
    }

    private ConnectionVersion4 onStartAction() {
        lock.lock();
        try {
            if (stopped) {
                throw new IllegalStateException("This connection has been stopped.");
            }
            executing.add(Thread.currentThread());
            if (connection == null) {
                // Hold the lock while creating the connection. Not generally good form.
                // In this instance, blocks other threads from creating the connection at the same time
                connection = implementationLoader.create(distribution);
            }
            return connection;
        } finally {
            lock.unlock();
        }
    }

    private void onEndAction() {
        lock.lock();
        try {
            executing.remove(Thread.currentThread());
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    private interface ConnectionAction<T> {
        T run(ConnectionVersion4 connection);
    }
}
