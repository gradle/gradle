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
import org.gradle.internal.io.ClassLoaderObjectInputStream;

import java.io.ByteArrayInputStream;
import java.util.concurrent.Callable;

/**
 * <p>Stage 3 of the start-up for a worker process with the application classes loaded in the system ClassLoader. Takes
 * care of adding the application classes to the system ClassLoader and then invoking the next stage of start-up.</p>
 *
 * <p> Instantiated in the worker bootstrap ClassLoader and invoked from {@link org.gradle.process.internal.launcher.BootstrapClassLoaderWorker}.
 * See {@link ApplicationClassesInSystemClassLoaderWorkerFactory} for details.</p>
 */
public class SystemApplicationClassLoaderWorker implements Callable<Void> {
    private final byte[] serializedWorker;

    public SystemApplicationClassLoaderWorker(byte[] serializedWorker) {
        this.serializedWorker = serializedWorker;
    }

    public Void call() throws Exception {
        ClassLoaderObjectInputStream instr = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), getClass().getClassLoader());
        final Action<WorkerContext> action = (Action<WorkerContext>) instr.readObject();

        action.execute(new WorkerContext() {
            public ClassLoader getApplicationClassLoader() {
                return ClassLoader.getSystemClassLoader();
            }
        });

        return null;
    }
}
