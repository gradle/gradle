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
import org.gradle.api.GradleException;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.DefaultLoggingConfigurer;
import org.gradle.initialization.LoggingConfigurer;
import org.gradle.util.*;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Collection;

public class BootstrapWorker implements Action<WorkerActionContext>, Serializable {
    private final LogLevel logLevel;
    private final Collection<String> sharedPackages;
    private final Collection<URL> implementationClassPath;
    private final byte[] serializedWorkerAction;

    protected BootstrapWorker(LogLevel logLevel, Collection<String> sharedPackages,
                              Collection<URL> implementationClassPath, Action<WorkerActionContext> workerAction) {
        this.logLevel = logLevel;
        this.sharedPackages = sharedPackages;
        this.implementationClassPath = implementationClassPath;
        serializedWorkerAction = GUtil.serialize(workerAction);
    }

    public void execute(WorkerActionContext workerActionContext) {
        LoggingConfigurer configurer = createLoggingConfigurer();
        configurer.configure(logLevel);

        FilteringClassLoader filteredWorkerClassLoader = new FilteringClassLoader(getClass().getClassLoader());
        filteredWorkerClassLoader.allowPackage("org.slf4j");
        filteredWorkerClassLoader.allowClass(Action.class);
        filteredWorkerClassLoader.allowClass(WorkerActionContext.class);

        ClassLoader applicationClassLoader = workerActionContext.getApplicationClassLoader();
        FilteringClassLoader filteredApplication = new FilteringClassLoader(applicationClassLoader);
        ObservableUrlClassLoader implementationClassLoader = createImplementationClassLoader(filteredWorkerClassLoader,
                filteredApplication);

        // Configure classpaths
        for (String sharedPackage : sharedPackages) {
            filteredApplication.allowPackage(sharedPackage);
        }
        implementationClassLoader.addURLs(implementationClassPath);

        // Deserialize the worker action
        Action<WorkerActionContext> action;
        try {
            ObjectInputStream instr = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorkerAction),
                    implementationClassLoader);
            action = (Action<WorkerActionContext>) instr.readObject();
        } catch (Exception e) {
            throw new GradleException(e);
        }
        action.execute(workerActionContext);
    }

    LoggingConfigurer createLoggingConfigurer() {
        return new DefaultLoggingConfigurer();
    }

    ObservableUrlClassLoader createImplementationClassLoader(ClassLoader system, ClassLoader application) {
        return new ObservableUrlClassLoader(new MultiParentClassLoader(application, system));
    }
}
