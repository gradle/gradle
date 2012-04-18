package org.gradle;

import org.junit.Test;

import static junit.framework.Assert.*;

public class JUnitTest {
    @Test
    public void mySecurityManagerIsUsed() throws ClassNotFoundException {
        assertTrue(System.getSecurityManager() instanceof MySecurityManager);
    }
}
