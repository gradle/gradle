package org.gradle;

import static junit.framework.Assert.assertTrue;

public class MyAgent {
    public static void premain(String args) {
        System.setProperty("using.custom.agent", "true");

        // This agent should be loaded via the custom system ClassLoader
        assertTrue(ClassLoader.getSystemClassLoader() instanceof MySystemClassLoader);
        assertTrue(MyAgent.class.getClassLoader() == ClassLoader.getSystemClassLoader());
    }
}
