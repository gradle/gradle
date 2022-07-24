package org.gradle;

import org.junit.Test;

import static junit.framework.Assert.*;

public class JUnitTest {
    @Test
    public void mySystemClassLoaderIsUsed() throws ClassNotFoundException {
        assertTrue(ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader);
        assertEquals(getClass().getClassLoader(), ClassLoader.getSystemClassLoader().getParent());
    }
}
