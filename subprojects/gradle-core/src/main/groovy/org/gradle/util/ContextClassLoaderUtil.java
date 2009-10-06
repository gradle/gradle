package org.gradle.util;

/**
 * @author Tom Eyckmans
 */
public class ContextClassLoaderUtil {

    static CustomContextClassLoaderRunnableWrapper createRunnableWrapper(ClassLoader customContextClassLoader, Runnable toWrapRunnable) {
        return new CustomContextClassLoaderRunnableWrapper(customContextClassLoader, toWrapRunnable);
    }

    public static void runWith(ClassLoader customContextClassLoader, Runnable toWrapRunnable)
    {
        final CustomContextClassLoaderRunnableWrapper runnableWrapper = createRunnableWrapper(customContextClassLoader, toWrapRunnable);

        runnableWrapper.run();
    }

    public static Thread withCustomInNewThread(ClassLoader customContextClassLoader, Runnable toWrapRunnable)
    {
        final CustomContextClassLoaderRunnableWrapper runnableWrapper = createRunnableWrapper(customContextClassLoader, toWrapRunnable);

        return ThreadUtils.run(runnableWrapper);
    }
}
