package org.gradle;

import org.testng.annotations.Test;

public class Test1 extends AbstractTest {

    @Test
    public void shouldPass() {
        assert 1 == value;
    }
}
