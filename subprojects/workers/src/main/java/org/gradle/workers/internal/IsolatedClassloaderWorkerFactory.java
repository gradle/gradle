/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.workers.internal;

import org.gradle.api.internal.AsmBackedClassGenerator;
import org.gradle.api.internal.DefaultInstantiatorFactory;
import org.gradle.api.internal.classloading.GroovySystemLoader;
import org.gradle.api.internal.classloading.GroovySystemLoaderFactory;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.serialize.ExceptionReplacingObjectInputStream;
import org.gradle.internal.serialize.ExceptionReplacingObjectOutputStream;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.util.GUtil;
import org.gradle.workers.IsolationMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.Callable;

public class IsolatedClassloaderWorkerFactory implements WorkerFactory {

    private final ClassLoaderFactory classLoaderFactory;
    private final WorkerLeaseRegistry workerLeaseRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final GroovySystemLoaderFactory groovySystemLoaderFactory = new GroovySystemLoaderFactory();

    public IsolatedClassloaderWorkerFactory(ClassLoaderFactory classLoaderFactory, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.classLoaderFactory = classLoaderFactory;
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Worker getWorker(final DaemonForkOptions forkOptions) {
        return new Worker() {
            @Override
            public DefaultWorkResult execute(ActionExecutionSpec spec) {
                return execute(spec, workerLeaseRegistry.getCurrentWorkerLease(), buildOperationExecutor.getCurrentOperation());
            }

            @Override
            public DefaultWorkResult execute(final ActionExecutionSpec spec, WorkerLease parentWorkerWorkerLease, final BuildOperationState parentBuildOperation) {
                WorkerLeaseRegistry.WorkerLeaseCompletion workerLease = parentWorkerWorkerLease.startChild();
                try {
                    return buildOperationExecutor.call(new CallableBuildOperation<DefaultWorkResult>() {
                        @Override
                        public DefaultWorkResult call(BuildOperationContext context) {
                            return executeInWorkerClassLoader(spec, forkOptions);
                        }

                        @Override
                        public BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName(spec.getDisplayName()).parent(parentBuildOperation);
                        }
                    });
                } finally {
                    workerLease.leaseFinish();
                }
            }
        };
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.CLASSLOADER;
    }

    private DefaultWorkResult executeInWorkerClassLoader(ActionExecutionSpec spec, DaemonForkOptions forkOptions) {
        ClassLoader actionClasspathLoader = createActionClasspathLoader(forkOptions);
        GroovySystemLoader actionClasspathGroovy = groovySystemLoaderFactory.forClassLoader(actionClasspathLoader);

        ClassLoader workerClassLoader = createWorkerClassLoader(actionClasspathLoader, forkOptions.getSharedPackages(), spec.getClass());

        ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(workerClassLoader);
            Callable<?> worker = transferWorkerIntoWorkerClassloader(spec, workerClassLoader);
            Object result = worker.call();
            return transferResultFromWorkerClassLoader(result);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            // Eventually shutdown any leaky groovy runtime loaded from action classpath loader
            actionClasspathGroovy.shutdown();
            Thread.currentThread().setContextClassLoader(previousContextLoader);
        }
    }

    private ClassLoader createActionClasspathLoader(DaemonForkOptions forkOptions) {
        return classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(forkOptions.getClasspath()));
    }

    private ClassLoader createWorkerClassLoader(ClassLoader actionClasspathLoader, Iterable<String> sharedPackages, Class<?> actionClass) {
        FilteringClassLoader.Spec actionFilterSpec = new FilteringClassLoader.Spec();
        for (String packageName : sharedPackages) {
            actionFilterSpec.allowPackage(packageName);
        }
        ClassLoader actionFilteredClasspathLoader = classLoaderFactory.createFilteringClassLoader(actionClasspathLoader, actionFilterSpec);

        FilteringClassLoader.Spec gradleApiFilterSpec = new FilteringClassLoader.Spec();
        // Logging
        gradleApiFilterSpec.allowPackage("org.slf4j");
        gradleApiFilterSpec.allowClass(Logger.class);
        gradleApiFilterSpec.allowClass(LogLevel.class);
        // Native
        gradleApiFilterSpec.allowPackage("org.gradle.internal.nativeintegration");
        gradleApiFilterSpec.allowPackage("org.gradle.internal.nativeplatform");
        gradleApiFilterSpec.allowPackage("net.rubygrapefruit.platform");
        // TODO:pm Add Gradle API and a way to opt out of it (for compiler workers)
        ClassLoader gradleApiLoader = classLoaderFactory.createFilteringClassLoader(actionClass.getClassLoader(), gradleApiFilterSpec);

        ClassLoader actionAndGradleApiLoader = new CachingClassLoader(new MultiParentClassLoader(gradleApiLoader, actionFilteredClasspathLoader));

        return new VisitableURLClassLoader(actionAndGradleApiLoader, ClasspathUtil.getClasspath(actionClass.getClassLoader()));
    }

    private Callable<?> transferWorkerIntoWorkerClassloader(ActionExecutionSpec spec, ClassLoader workerClassLoader) throws IOException, ClassNotFoundException {
        byte[] serializedWorker = GUtil.serialize(new WorkerCallable(spec));
        ObjectInputStream ois = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), workerClassLoader);
        return (Callable<?>) ois.readObject();
    }

    private DefaultWorkResult transferResultFromWorkerClassLoader(Object result) throws IOException, ClassNotFoundException {
        ByteArrayOutputStream resultBytes = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ExceptionReplacingObjectOutputStream(resultBytes);
        try {
            oos.writeObject(result);
        } finally {
            oos.close();
        }
        ObjectInputStream ois = new ExceptionReplacingObjectInputStream(new ByteArrayInputStream(resultBytes.toByteArray()), getClass().getClassLoader());
        return (DefaultWorkResult) ois.readObject();
    }

    /**
     * This is serialized across into the worker ClassLoader and then executed.
     */
    private static class WorkerCallable implements Callable<Object>, Serializable {
        private final ActionExecutionSpec spec;

        private WorkerCallable(ActionExecutionSpec spec) {
            this.spec = spec;
        }

        @Override
        public Object call() throws Exception {
            // TODO - reuse these services, either by making the global instances visible or by reusing the worker ClassLoaders and retaining a reference to them
            DefaultInstantiatorFactory instantiatorFactory = new DefaultInstantiatorFactory(new AsmBackedClassGenerator(), new CrossBuildInMemoryCacheFactory(new DefaultListenerManager()));
            WorkerProtocol<ActionExecutionSpec> worker = new DefaultWorkerServer(instantiatorFactory.inject());
            return worker.execute(spec);
        }
    }
}
