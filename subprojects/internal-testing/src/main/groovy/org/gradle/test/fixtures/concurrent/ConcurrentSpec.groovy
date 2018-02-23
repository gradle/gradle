/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.test.fixtures.concurrent

import org.gradle.internal.concurrent.ExecutorFactory
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.Executor

/**
 * A specification that uses multiple test threads. Provides an {@link Executor} and {@link org.gradle.internal.concurrent.ExecutorFactory} implementation.
 *
 * <p>This class maintains a set of <em>instants</em> reached by the test. An instant records the point in time that a test thread reached a certain point of its execution.
 * Once the test threads have completed, you can make assertions about the ordering of the various instants relative to each other. You can also block until a given
 * instant has been reached.
 *
 * <p>The main test thread cannot define instants, except within an {@link #async} block.
 *
 * <p>NOTE: Be careful when using this class with Spock mock objects, as these mocks perform some synchronisation of their own. This means that you may not be testing
 * what you think you are testing.
 */
@Timeout(60)
class ConcurrentSpec extends Specification {
    final TestLogger logger = new TestLogger()

    /**
     * An object that allows instants to be defined and queried.
     *
     * @see NamedInstant
     */
    final Instants instant = new Instants(logger)

    /**
     * An object that allows operations to be defined and queried.
     *
     * @see NamedOperation
     */
    final Operations operation = new Operations(instant, instant)

    /**
     * An object that allows control over the current thread.
     */
    final TestThread thread = new TestThread(instant)

    final BlockTarget waitFor = new BlockTarget(instant)

    private final TestExecutor executor = new TestExecutor(logger)
    private final TestExecutorFactory executorFactory = new TestExecutorFactory(executor)

    /**
     * Returns an Executor that should be used for running asynchronous actions.
     */
    Executor getExecutor() {
        return executor
    }

    /**
     * Returns an ExecutorFactory that should be used for running asynchronous actions.
     */
    ExecutorFactory getExecutorFactory() {
        return executorFactory
    }

    def setup() {
        instant.mainThread(Thread.currentThread())
    }

    def cleanup() {
        try {
            executor.stop(new Date(System.currentTimeMillis() + 5000))
        } finally {
            instant.mainThread(null)
        }
    }

    /**
     * Executes the given action and then blocks until all test threads have completed. The action may define instants for later querying outside the block.
     */
    void async(Runnable action) {
        Date timeout = new Date(System.currentTimeMillis() + 20000)
        executor.start()
        try {
            executor.execute(action)
        } finally {
            executor.stop(timeout)
        }
    }

    /**
     * Starts a test thread that will run the given action. The action may define instants for later querying.
     */
    void start(Runnable action) {
        executor.execute(action)
    }

    /**
     * Returns a range that contains the given milliseconds +/- some error margin
     */
    Range approx(long millis) {
        return new Range(millis)
    }
}
