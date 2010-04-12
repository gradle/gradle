/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.process.launcher;

import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * <p>A worker which loads the application classes in the system ClassLoader.</p>
 */
public class BootstrapClassLoaderWorker implements Callable<Void>, Serializable {
    private final Collection<URL> bootstrapClasspath;
    private final Collection<File> applicationClasspath;
    private final byte[] serializedWorker;

    public BootstrapClassLoaderWorker(Collection<URL> bootstrapClasspath, Collection<File> applicationClasspath, byte[] serializedWorker) {
        this.bootstrapClasspath = bootstrapClasspath;
        this.applicationClasspath = applicationClasspath;
        this.serializedWorker = serializedWorker;
    }

    public Void call() throws Exception {
        URL[] bootstrapUrls = bootstrapClasspath.toArray(new URL[bootstrapClasspath.size()]);
        URLClassLoader classLoader = new URLClassLoader(bootstrapUrls, ClassLoader.getSystemClassLoader().getParent());
        Class<? extends Callable> workerClass = classLoader.loadClass(
                "org.gradle.process.child.SystemApplicationClassLoaderWorker").asSubclass(Callable.class);
        Callable<Void> main = workerClass.getConstructor(Collection.class, byte[].class).newInstance(applicationClasspath, serializedWorker);
        return main.call();
    }
}