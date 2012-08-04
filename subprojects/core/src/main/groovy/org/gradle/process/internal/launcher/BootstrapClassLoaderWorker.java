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

package org.gradle.process.internal.launcher;

import java.io.Serializable;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * <p>Stage 2 of the start-up for a worker process with the application classes loaded in the system ClassLoader. Takes
 * care of creating the worker bootstrap ClassLoader and executing the next stage of start-up in that ClassLoader.</p>
 *
 * <p> Instantiated in the system ClassLoader and called from {@link GradleWorkerMain}. See
 * {@link org.gradle.process.internal.child.ApplicationClassesInSystemClassLoaderWorkerFactory} for details.</p>
 */
public class BootstrapClassLoaderWorker implements Callable<Void>, Serializable {
    private final Collection<URL> bootstrapClasspath;
    private final byte[] serializedWorker;

    public BootstrapClassLoaderWorker(Collection<URL> bootstrapClasspath, byte[] serializedWorker) {
        this.bootstrapClasspath = bootstrapClasspath;
        this.serializedWorker = serializedWorker;
    }

    public Void call() throws Exception {
        URL[] bootstrapUrls = bootstrapClasspath.toArray(new URL[bootstrapClasspath.size()]);
        URLClassLoader classLoader = new URLClassLoader(bootstrapUrls, ClassLoader.getSystemClassLoader().getParent());
        Class<? extends Callable> workerClass = classLoader.loadClass("org.gradle.process.internal.child.SystemApplicationClassLoaderWorker").asSubclass(Callable.class);
        Callable<Void> main = workerClass.getConstructor(byte[].class).newInstance(serializedWorker);
        return main.call();
    }
}