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

package org.gradle.tooling.internal.consumer.loader;

import org.gradle.logging.ProgressLogger;
import org.gradle.logging.ProgressLoggerFactory;
import org.gradle.tooling.internal.consumer.ConnectionParameters;
import org.gradle.tooling.internal.consumer.Distribution;
import org.gradle.tooling.internal.consumer.connection.ConsumerConnection;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SynchronizedToolingImplementationLoader implements ToolingImplementationLoader {

    Lock lock = new ReentrantLock();
    private final ToolingImplementationLoader delegate;

    public SynchronizedToolingImplementationLoader(ToolingImplementationLoader delegate) {
        this.delegate = delegate;
    }

    public ConsumerConnection create(Distribution distribution, ProgressLoggerFactory progressLoggerFactory, ConnectionParameters connectionParameters) {
        if (lock.tryLock()) {
            try {
                return delegate.create(distribution, progressLoggerFactory, connectionParameters);
            } finally {
                lock.unlock();
            }
        }
        ProgressLogger logger = progressLoggerFactory.newOperation(SynchronizedToolingImplementationLoader.class);
        logger.setDescription("Wait for the other thread to finish acquiring the distribution");
        logger.started();
        lock.lock();
        try {
            return delegate.create(distribution, progressLoggerFactory, connectionParameters);
        } finally {
            lock.unlock();
            logger.completed();
        }
    }
}
