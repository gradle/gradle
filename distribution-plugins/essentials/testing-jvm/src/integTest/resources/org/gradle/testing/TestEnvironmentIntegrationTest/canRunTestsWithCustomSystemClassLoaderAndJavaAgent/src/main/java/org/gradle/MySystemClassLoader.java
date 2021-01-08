package org.gradle;

import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * A custom ClassLoader that redefines the custom agent and the tests, to verify that they are
 * loaded via the custom system ClassLoader.
 */
public class MySystemClassLoader extends URLClassLoader {
    public MySystemClassLoader(ClassLoader parent) throws URISyntaxException, ClassNotFoundException {
        super(new URL[0], parent);
        // Should be constructed with the default system ClassLoader as root
        if (!getClass().getClassLoader().equals(parent)) {
            throw new AssertionError();
        }
        addClasspathFor("org.gradle.MyAgent");
        addClasspathFor("org.gradle.JUnitTest");
    }

    private void addClasspathFor(String name) throws ClassNotFoundException {
        URL codebase = getParent().loadClass(name).getProtectionDomain().getCodeSource().getLocation();
        addURL(codebase);
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        if (className.equals(getClass().getName())) {
            return getClass();
        }
        try {
            return findClass(className);
        } catch (ClassNotFoundException e) {
            return super.loadClass(className);
        }
    }
}
