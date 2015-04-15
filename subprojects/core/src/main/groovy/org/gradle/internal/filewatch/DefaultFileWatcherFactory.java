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

import org.gradle.api.JavaVersion;
import org.gradle.internal.Cast;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.reflect.DirectInstantiator;

import java.util.concurrent.ExecutorService;

/**
 * Implementation of {@link FileWatcherFactory}
 */
public class DefaultFileWatcherFactory implements FileWatcherFactory, Stoppable {
    private final ExecutorService executor;

    public DefaultFileWatcherFactory(ExecutorFactory executorFactory) {
        this.executor = executorFactory.create("filewatcher");
    }

    @Override
    public FileWatcher createFileWatcher() {
        if(JavaVersion.current().isJava7Compatible()) {
            Class clazz;
            try {
                clazz = this.getClass().getClassLoader().loadClass("org.gradle.internal.filewatch.jdk7.DefaultFileWatcher");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("Cannot find FileWatcher implementation class", e);
            }
            return Cast.uncheckedCast(DirectInstantiator.instantiate(clazz, executor));
        } else {
            throw new RuntimeException("FileWatcher is only available on Java 7 compatible JVMs");
        }
    }

    @Override
    public void stop() {
        executor.shutdown();
    }

}
