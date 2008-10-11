package org.gradle.execution

public class DefaultTaskExecuterTestHelper {
    public static Closure toClosure(Runnable runnable) {
        { taskGraph -> runnable.run() }
    }
}