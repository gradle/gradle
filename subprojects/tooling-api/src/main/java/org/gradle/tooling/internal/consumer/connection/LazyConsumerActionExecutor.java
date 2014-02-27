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
package org.gradle.tooling.internal.consumer.connection;

import org.gradle.internal.UncheckedException;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.LoggingProvider;
import org.gradle.tooling.internal.consumer.loader.ToolingImplementationLoader;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Creates the actual executor implementation on demand.
 */
public class LazyConsumerActionExecutor implements ConsumerActionExecutor {
    private final Distribution distribution;
    private final ToolingImplementationLoader implementationLoader;
    private final LoggingProvider loggingProvider;

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    private final Set<Thread> executing = new HashSet<Thread>();
    private boolean stopped;
    private ConsumerConnection connection;

    private final ConnectionParameters connectionParameters;

    public LazyConsumerActionExecutor(Distribution distribution, ToolingImplementationLoader implementationLoader, LoggingProvider loggingProvider, ConnectionParameters connectionParameters) {
        this.distribution = distribution;
        this.implementationLoader = implementationLoader;
        this.loggingProvider = loggingProvider;
        this.connectionParameters = connectionParameters;
    }

    public void stop() {
        ConsumerConnection connection = null;
        lock.lock();
        try {
            stopped = true;
            while (!executing.isEmpty()) {
                try {
                    condition.await();
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
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

    public String getDisplayName() {
        return distribution.getDisplayName();
    }

    public <T> T run(ConsumerAction<T> action) throws UnsupportedOperationException, IllegalStateException {
        try {
            ConsumerConnection connection = onStartAction();
            return action.run(connection);
        } finally {
            onEndAction();
        }
    }

    private ConsumerConnection onStartAction() {
        lock.lock();
        try {
            if (stopped) {
                throw new IllegalStateException("This connection has been stopped.");
            }
            executing.add(Thread.currentThread());
            if (connection == null) {
                // Hold the lock while creating the connection. Not generally good form.
                // In this instance, blocks other threads from creating the connection at the same time
                connection = implementationLoader.create(distribution, loggingProvider.getProgressLoggerFactory(), connectionParameters);
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
}
