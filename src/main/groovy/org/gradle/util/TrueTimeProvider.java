package org.gradle.util;

public class TrueTimeProvider implements TimeProvider {

    public long getCurrentTime() {
        return System.currentTimeMillis();
    }

}
