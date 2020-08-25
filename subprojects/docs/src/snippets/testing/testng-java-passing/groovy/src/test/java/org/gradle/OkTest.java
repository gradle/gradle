package org.gradle;

public class OkTest {
    @org.testng.annotations.Test
    public void passingTest() {
    }

    @org.testng.annotations.Test(expectedExceptions = RuntimeException.class)
    public void expectedFailTest() {
        throw new RuntimeException("broken");
    }
}
