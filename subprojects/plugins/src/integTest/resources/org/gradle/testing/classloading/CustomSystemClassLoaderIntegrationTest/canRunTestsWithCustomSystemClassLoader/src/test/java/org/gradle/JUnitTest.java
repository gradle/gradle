package org.gradle;

import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

public class JUnitTest {
    @Test
    public void mySystemClassLoaderIsUsed() {
        assertTrue(ClassLoader.getSystemClassLoader() instanceof org.gradle.MySystemClassLoader);
        assertEquals("true", System.getProperty("using.custom.class.loader"));
    }
}
