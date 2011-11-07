package org.gradle;

import org.junit.Test;

import static org.junit.Assert.fail;

public class FailingWithDisplayViolationsIsTrueTest {
    @Test
    public void failure() {
        fail("failed");
    }
}