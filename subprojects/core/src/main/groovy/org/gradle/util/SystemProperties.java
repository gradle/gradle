package org.gradle.util;

/**
 * Provides access to frequently used system properties.
 */
public class SystemProperties {
    public static String getLineSeparator() {
        return System.getProperty("line.separator");
    }

    public static String getJavaIoTmpDir() {
        return System.getProperty("java.io.tmpdir");
    }
}
