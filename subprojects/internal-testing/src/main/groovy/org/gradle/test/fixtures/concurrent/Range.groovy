package org.gradle.test.fixtures.concurrent

/**
 * A range of time.
 */
class Range {
    private final long millis

    Range(long millis) {
        this.millis = millis
    }

    @Override
    String toString() {
        return "[approx $millis millis]"
    }

    boolean isCase(Duration duration) {
        def actualMillis = duration.millis
        return actualMillis > millis - 500 && actualMillis < millis + 2000
    }
}
