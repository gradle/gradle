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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.concurrent.Callable;

/**
 * <p>Stage 2 of the start-up for a worker process with the application classes loaded in the system ClassLoader. Takes
 * care of deserializing and invoking the worker action.</p>
 *
 * <p> Instantiated in the implementation ClassLoader and invoked from {@link org.gradle.process.internal.launcher.GradleWorkerMain}.
 * See {@link ApplicationClassesInSystemClassLoaderWorkerFactory} for details.</p>
 */
public class SystemApplicationClassLoaderWorker implements Callable<Void> {
    private final int logLevel;
    private final byte[] serializedWorker;

    public SystemApplicationClassLoaderWorker(int logLevel, byte[] serializedWorker) {
        this.logLevel = logLevel;
        this.serializedWorker = serializedWorker;
    }

    public Void call() throws Exception {
        LoggingManagerInternal loggingManager = createLoggingManager();
        loggingManager.setLevel(LogLevel.values()[logLevel]).start();

        // Deserialize the worker action
        Action<WorkerContext> action;
        try {
            ObjectInputStream instr = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), getClass().getClassLoader());
            action = (Action<WorkerContext>) instr.readObject();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        action.execute(new WorkerContext() {
            public ClassLoader getApplicationClassLoader() {
                return ClassLoader.getSystemClassLoader();
            }
        });

        return null;
    }

    LoggingManagerInternal createLoggingManager() {
        return LoggingServiceRegistry.newCommandLineProcessLogging().newInstance(LoggingManagerInternal.class);
    }
}
