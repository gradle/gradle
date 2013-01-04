package org.gradle;

import org.junit.Assume;
import org.junit.Ignore;
import org.junit.Test;

public class Junit4Test {
    @Test
    public void ok() {
    }

    @Test @Ignore
    public void broken() {
        throw new RuntimeException();
    }

    @Test
    public void assumptionFailed() {
        Assume.assumeTrue(false);
    }
}
