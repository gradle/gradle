package org.gradle;

import java.security.Permission;

import static org.junit.Assert.assertEquals;

public class MySecurityManager extends SecurityManager {
    public MySecurityManager() {
        assertEquals(getClass().getName(), System.getProperty("java.security.manager"));
    }

    @Override
    public void checkPermission(Permission permission) {
    }
}
