package org.gradle.sample;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.fail;

class SampleTests {

    @Test
    void successful() {
        new Sample().otherFunctionality();
    }

    @Test
    void flaky() {
        new Sample().functionality();
    }

    @Test
    void failing() {
        fail();
    }
}
