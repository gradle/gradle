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
import org.gradle.internal.concurrent.ManagedExecutor
import org.gradle.internal.concurrent.ManagedScheduledExecutor
import org.gradle.internal.concurrent.ManagedThreadPoolExecutor

import java.util.concurrent.TimeUnit

class TestExecutorFactory implements ExecutorFactory {
    private final TestExecutor executor

    TestExecutorFactory(TestExecutor executor) {
        this.executor = executor
    }

    @Override
    ManagedExecutor create(String displayName) {
        return new TestManagedExecutor(executor)
    }

    @Override
    ManagedExecutor create(String displayName, int fixedSize) {
        // Ignores size of thread pool
        return new TestManagedExecutor(executor)
    }

    @Override
    ManagedThreadPoolExecutor createThreadPool(String displayName, int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit timeUnit) {
        throw new UnsupportedOperationException()
    }

    @Override
    ManagedScheduledExecutor createScheduled(String displayName, int fixedSize) {
        throw new UnsupportedOperationException()
    }
}
