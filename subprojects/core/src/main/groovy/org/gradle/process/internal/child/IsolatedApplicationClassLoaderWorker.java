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

package org.gradle.process.internal.child;

import org.gradle.api.Action;
import org.gradle.internal.classloader.DefaultClassLoaderFactory;

import java.io.Serializable;
import java.net.URI;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * <p>A worker which loads the application classes in an isolated ClassLoader.</p>
 */
public class IsolatedApplicationClassLoaderWorker implements Callable<Void>, Serializable {
    private final Action<WorkerContext> worker;
    private final Collection<URI> applicationClassPath;

    public IsolatedApplicationClassLoaderWorker(Collection<URI> applicationClassPath, Action<WorkerContext> worker) {
        this.applicationClassPath = applicationClassPath;
        this.worker = worker;
    }

    public Void call() throws Exception {
        final ClassLoader applicationClassLoader = createApplicationClassLoader();

        WorkerContext context = new WorkerContext() {
            public ClassLoader getApplicationClassLoader() {
                return applicationClassLoader;
            }
        };

        worker.execute(context);

        return null;
    }

    private ClassLoader createApplicationClassLoader() {
        return new DefaultClassLoaderFactory().createIsolatedClassLoader(applicationClassPath);
    }
}
