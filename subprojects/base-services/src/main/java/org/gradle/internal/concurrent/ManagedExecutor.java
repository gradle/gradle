/*
 * Copyright 2010 the original author or authors.
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

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public interface ManagedExecutor extends AsyncStoppable, ExecutorService {
    /**
     * Stops accepting new jobs and blocks until all currently executing jobs have been completed.
     */
    @Override
    void stop();

    /**
     * Stops accepting new jobs and blocks until all currently executing jobs have been completed. Once the given
     * timeout has been reached, forcefully stops remaining jobs and throws an exception.
     *
     * @throws IllegalStateException on timeout.
     */
    void stop(int timeoutValue, TimeUnit timeoutUnits) throws IllegalStateException;

    /**
     * Sets the fixed size of the thread pool for the executor.
     */
    void setFixedPoolSize(int numThreads);

    /**
     * Sets the keep alive time for the thread pool of the executor.
     */
    void setKeepAlive(int timeout, TimeUnit timeUnit);
}
