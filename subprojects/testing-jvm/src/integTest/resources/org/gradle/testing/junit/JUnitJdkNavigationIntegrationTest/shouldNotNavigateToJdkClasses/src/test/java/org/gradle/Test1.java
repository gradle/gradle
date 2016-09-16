package org.gradle;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Test1 extends AbstractTest {

    @Test
    public void shouldPass() {
        assertEquals(1, value);
    }
}
