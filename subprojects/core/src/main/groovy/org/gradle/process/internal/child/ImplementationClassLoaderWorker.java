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
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classloader.MutableURLClassLoader;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.logging.LoggingManagerInternal;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.util.*;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Collection;

/**
 * <p>A stage of the worker process start-up. Instantiated in the worker bootstrap ClassLoader and takes care of
 * creating the implementation ClassLoader and executing the next stage of start-up in that ClassLoader. </p>
 */
public class ImplementationClassLoaderWorker implements Action<WorkerContext>, Serializable {
    private final LogLevel logLevel;
    private final Collection<String> sharedPackages;
    private final Collection<URL> implementationClassPath;
    private final byte[] serializedWorkerAction;

    protected ImplementationClassLoaderWorker(LogLevel logLevel, Collection<String> sharedPackages,
                                              Collection<URL> implementationClassPath,
                                              Action<WorkerContext> workerAction) {
        this.logLevel = logLevel;
        this.sharedPackages = sharedPackages;
        this.implementationClassPath = implementationClassPath;
        serializedWorkerAction = GUtil.serialize(workerAction);
    }

    public void execute(WorkerContext workerContext) {
        LoggingManagerInternal loggingManager = createLoggingManager();
        loggingManager.setLevel(logLevel).start();

        FilteringClassLoader filteredWorkerClassLoader = new FilteringClassLoader(getClass().getClassLoader());
        filteredWorkerClassLoader.allowPackage("org.slf4j");
        filteredWorkerClassLoader.allowClass(Action.class);
        filteredWorkerClassLoader.allowClass(WorkerContext.class);

        ClassLoader applicationClassLoader = workerContext.getApplicationClassLoader();
        FilteringClassLoader filteredApplication = new FilteringClassLoader(applicationClassLoader);
        MutableURLClassLoader implementationClassLoader = createImplementationClassLoader(filteredWorkerClassLoader,
                filteredApplication);

        // Configure classpaths
        for (String sharedPackage : sharedPackages) {
            filteredApplication.allowPackage(sharedPackage);
        }
        implementationClassLoader.addURLs(implementationClassPath);

        // Deserialize the worker action
        Action<WorkerContext> action;
        try {
            ObjectInputStream instr = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorkerAction),
                    implementationClassLoader);
            action = (Action<WorkerContext>) instr.readObject();
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
        action.execute(workerContext);
    }

    LoggingManagerInternal createLoggingManager() {
        return LoggingServiceRegistry.newProcessLogging().newInstance(LoggingManagerInternal.class);
    }

    MutableURLClassLoader createImplementationClassLoader(ClassLoader system, ClassLoader application) {
        return new MutableURLClassLoader(new CachingClassLoader(new MultiParentClassLoader(application, system)));
    }
}
