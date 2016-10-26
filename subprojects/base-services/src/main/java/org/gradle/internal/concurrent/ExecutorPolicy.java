/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.concurrent;

import org.gradle.internal.UncheckedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Controls the behavior of an executor when a task is executed and an executor is stopped.
 */
public interface ExecutorPolicy {
    /**
     * Special behavior when a task is executed.
     *
     * The Runnable's run() needs to be called from this method.
     */
    void onExecute(Runnable command);

    /**
     * Special behavior after an executor is stopped.
     *
     * This is called after the underlying Executor has been stopped.
     */
    void onStop();

    /**
     * Runs the Runnable, catches all Throwables and logs them.
     *
     * The first exception caught during onExecute(), will be rethrown in onStop().
     */
    class CatchAndRecordFailures implements ExecutorPolicy {
        private static final Logger LOGGER = LoggerFactory.getLogger(DefaultExecutorFactory.class);
        private final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();

        public void onExecute(Runnable command) {
            try {
                command.run();
            } catch (Throwable throwable) {
                onFailure(String.format("Failed to execute %s.", command), throwable);
            }
        }

        public void onFailure(String message, Throwable throwable) {
            // Capture or log all failures
            if (!failure.compareAndSet(null, throwable)) {
                LOGGER.error(message, throwable);
            }
        }

        public void onStop() {
            // Rethrow the first failure
            Throwable failure = this.failure.getAndSet(null);
            if (failure != null) {
                throw UncheckedException.throwAsUncheckedException(failure);
            }
        }
    }
}
