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

package org.gradle.process.child;

import org.gradle.api.Action;
import org.gradle.util.ObservableUrlClassLoader;

import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * <p>A worker which loads the application classes in an isolated ClassLoader.</p>
 *
 * <p>Class loader hierarchy:</p>
 * <pre>
 *                         bootstrap
 *                            |
 *              +-------------+------------+
 *              |                          |
 *            system                   application
 *  (bootstrap classes, logging)           |
 *              |                          |
 *           filter                     filter
 *         (logging)               (shared packages)
 *              |                         |
 *              +-------------+-----------+
 *                            |
 *                     implementation
 *           (WorkerMain + action implementation)
 * </pre>
 */
public class IsolatedClassLoaderWorker implements Callable<Void>, Serializable {
    private final Action<WorkerActionContext> worker;
    private final Collection<URL> applicationClassPath;

    public IsolatedClassLoaderWorker(Collection<URL> applicationClassPath, Action<WorkerActionContext> worker) {
        this.applicationClassPath = applicationClassPath;
        this.worker = worker;
    }

    public Void call() throws Exception {
        final ObservableUrlClassLoader applicationClassLoader = createApplicationClassLoader();

        WorkerActionContext context = new WorkerActionContext() {
            public ClassLoader getApplicationClassLoader() {
                return applicationClassLoader;
            }
        };

        worker.execute(context);

        return null;
    }

    private ObservableUrlClassLoader createApplicationClassLoader() {
        return new ObservableUrlClassLoader(ClassLoader.getSystemClassLoader().getParent(), applicationClassPath);
    }
}
