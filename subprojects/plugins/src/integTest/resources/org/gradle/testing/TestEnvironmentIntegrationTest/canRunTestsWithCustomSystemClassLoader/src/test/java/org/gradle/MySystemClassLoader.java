package org.gradle;

import java.net.URL;
import java.net.URLClassLoader;
import static junit.framework.Assert.*;

public class MySystemClassLoader extends URLClassLoader {
    public MySystemClassLoader(ClassLoader parent) {
        super(new URL[0], parent);
        // Should be constructed with the default system ClassLoader as root
        assertEquals(getClass().getClassLoader(), parent);
    }
}
