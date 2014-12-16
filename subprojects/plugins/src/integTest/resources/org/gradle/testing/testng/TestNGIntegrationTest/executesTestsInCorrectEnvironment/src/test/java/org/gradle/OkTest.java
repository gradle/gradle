package org.gradle;

import static org.testng.Assert.*;

public class OkTest {
    @org.testng.annotations.Test
    public void ok() throws Exception {
        // check working dir
        assertEquals(System.getProperty("testDir"), System.getProperty("user.dir"));

        // check Gradle classes not visible
        try {
            getClass().getClassLoader().loadClass("org.gradle.api.Project");
            fail();
        } catch (ClassNotFoundException e) {
            // Expected
        }

        // check context classloader
        assertSame(getClass().getClassLoader(), Thread.currentThread().getContextClassLoader());

        // check sys properties
        assertEquals("value", System.getProperty("testSysProperty"));

        // check env vars
        assertEquals("value", System.getenv("TEST_ENV_VAR"));

        // check other environmental stuff
        assertEquals("Test worker", Thread.currentThread().getName());
        assertNull(System.getSecurityManager());
    }
}
