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

package org.gradle.process;

import org.gradle.api.Action;
import org.gradle.api.logging.LogLevel;
import org.gradle.initialization.DefaultLoggingConfigurer;
import org.gradle.initialization.LoggingConfigurer;
import org.gradle.util.ClassLoaderObjectInputStream;
import org.gradle.util.FilteringClassLoader;
import org.gradle.util.MultiParentClassLoader;
import org.gradle.util.ObservableUrlClassLoader;

import java.io.ObjectInputStream;
import java.lang.reflect.Constructor;
import java.net.URI;
import java.net.URL;
import java.util.Collection;

public class GradleWorkerMain {
    public void run() throws Exception {

        //
        // Class loader hierarchy
        //
        //                    bootstrap
        //                        |
        //            +-----------+----------+
        //            |                      |
        //          system              application
        //  (this class, logging)            |
        //            |                      |
        //         filter                 filter
        //            |                      |
        //            +----------------------+
        //                          |
        //                   implementation
        //         (WorkerMain + action implementation)
        //

        LoggingConfigurer configurer = createLoggingConfigurer();
        configurer.configure(LogLevel.LIFECYCLE);

        FilteringClassLoader filteredSystem = new FilteringClassLoader(getClass().getClassLoader());
        filteredSystem.allowPackage("org.slf4j");

        ObservableUrlClassLoader sharedClassLoader = createSharedClassLoader();
        FilteringClassLoader filteredShared = new FilteringClassLoader(sharedClassLoader);
        ObservableUrlClassLoader implementationClassLoader = createImplementationClassLoader(filteredSystem, filteredShared);

        ObjectInputStream instr = new ClassLoaderObjectInputStream(System.in, implementationClassLoader);

        // Configure logging
        LogLevel logLevel = (LogLevel) instr.readObject();
        configurer.configure(logLevel);

        // Configure classpaths
        sharedClassLoader.addURLs((Collection<URL>) instr.readObject());
        Collection<String> sharedPackages = (Collection<String>) instr.readObject();
        for (String sharedPackage : sharedPackages) {
            filteredShared.allowPackage(sharedPackage);
        }
        implementationClassLoader.addURLs((Iterable<URL>) instr.readObject());

        // Read worker action
        Object action = instr.readObject();
        URI serverAddress = (URI) instr.readObject();

        Class<? extends Runnable> workerClass = implementationClassLoader.loadClass(WorkerMain.class.getName())
                .asSubclass(Runnable.class);
        Constructor<? extends Runnable> constructor = workerClass.getConstructor(implementationClassLoader.loadClass(
                Action.class.getName()), URI.class, ClassLoader.class);
        Runnable worker = constructor.newInstance(action, serverAddress, sharedClassLoader);
        worker.run();
    }

    protected LoggingConfigurer createLoggingConfigurer() {
        return new DefaultLoggingConfigurer();
    }

    protected ObservableUrlClassLoader createSharedClassLoader() {
        return new ObservableUrlClassLoader(ClassLoader.getSystemClassLoader().getParent());
    }

    protected ObservableUrlClassLoader createImplementationClassLoader(ClassLoader system, ClassLoader application) {
        return new ObservableUrlClassLoader(new MultiParentClassLoader(application, system));
    }

    public static void main(String[] args) {
        try {
            new GradleWorkerMain().run();
            System.exit(0);
        } catch (Throwable throwable) {
            throwable.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
