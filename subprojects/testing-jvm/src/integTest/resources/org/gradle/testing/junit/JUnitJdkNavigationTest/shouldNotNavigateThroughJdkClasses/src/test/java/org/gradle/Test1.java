package org.gradle;

import org.junit.Test;

public class Test1 extends AbstractTest {

    @Test
    public void shouldPass() {
        org.junit.Assert.assertEquals(1, value);
    }
}
