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
import org.gradle.api.logging.LogLevel;

import java.net.URL;
import java.util.Collection;
import java.util.concurrent.Callable;

/**
 * <p>Stage 2 of the start-up for a worker process with the application classes loaded in the system ClassLoader. Takes
 * care of deserializing and then invoking the next stage of start-up.</p>
 *
 * <p> Instantiated in the infrastructure ClassLoader and invoked from {@link org.gradle.process.internal.launcher.GradleWorkerMain}.
 * See {@link ApplicationClassesInSystemClassLoaderWorkerFactory} for details.</p>
 */
public class SystemApplicationClassLoaderWorker implements Callable<Void> {
    private final int logLevel;
    private final Collection<String> sharedPackages;
    private final Collection<URL> workerClassPath;
    private final byte[] serializedWorker;

    public SystemApplicationClassLoaderWorker(int logLevel, Collection<String> sharedPackages, Collection<URL> workerClassPath, byte[] serializedWorker) {
        this.logLevel = logLevel;
        this.sharedPackages = sharedPackages;
        this.workerClassPath = workerClassPath;
        this.serializedWorker = serializedWorker;
    }

    public Void call() throws Exception {
        final Action<WorkerContext> action = new ImplementationClassLoaderWorker(LogLevel.values()[logLevel], sharedPackages, workerClassPath, serializedWorker);

        action.execute(new WorkerContext() {
            public ClassLoader getApplicationClassLoader() {
                return ClassLoader.getSystemClassLoader();
            }
        });

        return null;
    }
}
