/*
 * Copyright 2009 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
