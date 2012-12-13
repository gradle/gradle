package org.gradle.test.fixtures.concurrent

import org.gradle.internal.concurrent.ExecutorFactory
import org.gradle.internal.concurrent.StoppableExecutor

class TestExecutorFactory implements ExecutorFactory {
    private final TestExecutor executor

    TestExecutorFactory(TestExecutor executor) {
        this.executor = executor
    }

    StoppableExecutor create(String displayName) {
        return new TestStoppableExecutor(executor)
    }
}
