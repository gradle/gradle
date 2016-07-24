package org.gradle;

import org.gradle.AbstractTest;
import org.junit.Test;

public class Test extends AbstractTest {

    @Test
    public void shouldPass() {
        org.junit.Assert.assertEquals(1, value);
    }
}
