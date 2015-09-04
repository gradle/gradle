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

package org.gradle.internal.filewatch;

import org.gradle.api.Action;
import org.gradle.api.JavaVersion;
import org.gradle.api.internal.file.FileSystemSubset;
import org.gradle.internal.Cast;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.reflect.DirectInstantiator;

import java.util.concurrent.ExecutorService;

public class DefaultFileWatcherFactory implements FileWatcherFactory, Stoppable {
    private final ExecutorService executor;
    private final JavaVersion javaVersion;
    private final ClassLoader classLoader;

    private FileWatcherFactory fileWatcherFactory;

    public DefaultFileWatcherFactory(ExecutorFactory executorFactory) {
        this(JavaVersion.current(), DefaultFileWatcherFactory.class.getClassLoader(), executorFactory);
    }

    DefaultFileWatcherFactory(JavaVersion javaVersion, ClassLoader classLoader, ExecutorFactory executorFactory) {
        this.javaVersion = javaVersion;
        this.classLoader = classLoader;
        this.executor = executorFactory.create("filewatcher");
    }

    protected FileWatcherFactory createFileWatcherFactory() {
        if (javaVersion.isJava7Compatible()) {
            try {
                Class clazz = classLoader.loadClass("org.gradle.internal.filewatch.jdk7.Jdk7FileWatcherFactory");
                return Cast.uncheckedCast(DirectInstantiator.instantiate(clazz, executor));
            } catch (ClassNotFoundException e) {
                throw UncheckedException.throwAsUncheckedException(e);
            }
        } else {
            throw new UnsupportedOperationException("File watching requires Java 7 or later.");
        }
    }

    @Override
    public void stop() {
        executor.shutdown();
    }

    @Override
    public FileWatcher watch(FileSystemSubset systemSubset, Action<? super Throwable> onError, FileWatcherListener listener) {
        if (fileWatcherFactory == null) {
            fileWatcherFactory = createFileWatcherFactory();
        }
        return fileWatcherFactory.watch(systemSubset, onError, listener);
    }
}
