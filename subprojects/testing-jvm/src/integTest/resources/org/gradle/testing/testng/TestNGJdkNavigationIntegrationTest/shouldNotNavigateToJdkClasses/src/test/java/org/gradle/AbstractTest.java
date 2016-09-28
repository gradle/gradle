package org.gradle;

import org.testng.annotations.BeforeTest;

public abstract class AbstractTest {

    protected int value = 0;

    @BeforeTest
    public void before() {
        value = 1;
    }
}
