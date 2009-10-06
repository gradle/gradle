package org.gradle.util;

/**
 * @author Tom Eyckmans
 */
public class CustomContextClassLoaderRunnableWrapper implements Runnable {

    private final ClassLoader customClassLoader;
    private final Runnable toWrapRunnable;

    public CustomContextClassLoaderRunnableWrapper(ClassLoader customClassLoader, Runnable toWrapRunnable) {
        if ( customClassLoader == null ) throw new IllegalArgumentException("customClassLoader == null!");
        if ( toWrapRunnable == null ) throw new IllegalArgumentException("toWrapRunnable == null!");
        this.customClassLoader = customClassLoader;
        this.toWrapRunnable = toWrapRunnable;
    }

    public void run() {
        final ClassLoader previousContextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(customClassLoader);

            toWrapRunnable.run();
        }
        finally {
            Thread.currentThread().setContextClassLoader(previousContextClassLoader);
        }
    }
}
