package org.gradle.execution

public class BuildExecuterTestHelper {
    public static Closure toClosure(Runnable runnable) {
        { taskGraph -> runnable.run() }
    }
}