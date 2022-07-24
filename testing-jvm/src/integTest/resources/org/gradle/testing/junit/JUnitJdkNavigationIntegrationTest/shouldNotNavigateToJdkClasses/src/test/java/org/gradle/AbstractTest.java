package org.gradle;

import org.junit.Before;

public abstract class AbstractTest {

    protected int value = 0;

    @Before
    public void before() {
        value = 1;
    }
}
