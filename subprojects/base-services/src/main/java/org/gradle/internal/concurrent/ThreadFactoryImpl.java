/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.concurrent;

import javax.annotation.Nullable;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Provides meaningful names to threads created in a thread pool.
 * And mark them as managed by Gradle.
 */
public class ThreadFactoryImpl implements ThreadFactory {
    private final AtomicLong counter = new AtomicLong();
    private final String displayName;
    @Nullable
    private final ClassLoader contextClassloader;

    public ThreadFactoryImpl(String displayName, @Nullable ClassLoader contextClassloader) {
        this.displayName = displayName;
        this.contextClassloader = contextClassloader;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(new ManagedThreadRunnable(r));
        thread.setName(nextThreadName());
        thread.setContextClassLoader(contextClassloader);
        return thread;
    }

    private String nextThreadName() {
        long count = counter.incrementAndGet();
        return count == 1 ? displayName : displayName + " Thread " + count;
    }

    private static class ManagedThreadRunnable implements Runnable {
        private final Runnable delegate;

        private ManagedThreadRunnable(Runnable delegate) {
            this.delegate = delegate;
        }

        @Override
        public void run() {
            GradleThread.setManaged();
            delegate.run();
        }
    }
}
