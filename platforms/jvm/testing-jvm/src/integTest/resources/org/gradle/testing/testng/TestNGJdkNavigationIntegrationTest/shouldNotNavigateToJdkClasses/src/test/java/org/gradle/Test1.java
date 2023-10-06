package org.gradle;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

public class Test1 extends AbstractTest {

    @Test
    public void shouldPass() {
        assertEquals(1, value);
    }
}
