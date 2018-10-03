package org.gradle;

import java.net.URL;
import java.net.URLClassLoader;

public class MySystemClassLoader extends URLClassLoader {
    public MySystemClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        // Should be constructed with the default system ClassLoader as root
        if (!getClass().getClassLoader().equals(parent)) {
            throw new AssertionError();
        }
    }
}
