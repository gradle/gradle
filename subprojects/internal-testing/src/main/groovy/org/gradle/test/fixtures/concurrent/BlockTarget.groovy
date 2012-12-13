package org.gradle.test.fixtures.concurrent

/**
 * A target for blocking. Exposes each named instant as a property, and accessing that property will block until the
 * instant has been reached.
 */
class BlockTarget {
    private final Instants instants

    BlockTarget(Instants instants) {
        this.instants = instants
    }

    def getProperty(String name) {
        instants.waitFor(name)
        Thread.sleep(500)
        return null
    }
}
