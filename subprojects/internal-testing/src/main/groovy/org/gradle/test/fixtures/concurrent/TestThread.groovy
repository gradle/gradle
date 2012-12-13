package org.gradle.test.fixtures.concurrent

/**
 * Represents the current thread.
 */
class TestThread {
    private final Instants instants

    TestThread(Instants instants) {
        this.instants = instants
    }

    void block() {
        Thread.sleep(500)
    }

    BlockTarget getBlockUntil() {
        return new BlockTarget(instants)
    }
}
