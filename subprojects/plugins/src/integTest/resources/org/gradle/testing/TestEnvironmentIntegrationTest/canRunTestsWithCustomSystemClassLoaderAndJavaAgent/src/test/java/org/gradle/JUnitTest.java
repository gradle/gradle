package org.gradle;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class JUnitTest {
    @Test
    public void mySystemClassLoaderIsUsed() throws ClassNotFoundException {
        assertEquals("true", System.getProperty("using.custom.agent"));

        // This test class should be loaded via the custom system ClassLoader
        assertTrue(ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader);
        assertTrue(getClass().getClassLoader() == ClassLoader.getSystemClassLoader());
    }
}
