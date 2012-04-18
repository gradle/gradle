package org.gradle;

import org.junit.Test;

import static junit.framework.Assert.*;
import static org.junit.Assert.assertEquals;

public class JUnitTest {
    @Test
    public void mySecurityManagerIsUsed() throws ClassNotFoundException {
        assertTrue(System.getSecurityManager() instanceof MySecurityManager);
        assertEquals(ClassLoader.getSystemClassLoader(), MySecurityManager.class.getClassLoader());
    }
}
