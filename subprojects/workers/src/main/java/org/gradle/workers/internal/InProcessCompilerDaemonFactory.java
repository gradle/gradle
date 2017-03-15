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

import org.gradle.api.Transformer;
import org.gradle.api.internal.classloading.GroovySystemLoader;
import org.gradle.api.internal.classloading.GroovySystemLoaderFactory;
import org.gradle.api.logging.LogLevel;
import org.gradle.api.logging.Logger;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.operations.BuildOperationWorkerRegistry.Operation;
import org.gradle.internal.progress.BuildOperationDetails;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.serialize.ExceptionReplacingObjectInputStream;
import org.gradle.internal.serialize.ExceptionReplacingObjectOutputStream;
import org.gradle.util.GUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.concurrent.Callable;

public class InProcessCompilerDaemonFactory implements WorkerDaemonFactory {

    private final ClassLoaderFactory classLoaderFactory;
    private final BuildOperationWorkerRegistry buildOperationWorkerRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final GroovySystemLoaderFactory groovySystemLoaderFactory = new GroovySystemLoaderFactory();


    public InProcessCompilerDaemonFactory(ClassLoaderFactory classLoaderFactory, BuildOperationWorkerRegistry buildOperationWorkerRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.classLoaderFactory = classLoaderFactory;
        this.buildOperationWorkerRegistry = buildOperationWorkerRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public WorkerDaemon getDaemon(Class<? extends WorkerDaemonProtocol> serverImplementationClass, File workingDir, final DaemonForkOptions forkOptions) {
        return new WorkerDaemon() {
            @Override
            public <T extends WorkSpec> DefaultWorkResult execute(WorkerDaemonAction<T> action, T spec) {
                return execute(action, spec, buildOperationWorkerRegistry.getCurrent(), buildOperationExecutor.getCurrentOperation());
            }

            @Override
            public <T extends WorkSpec> DefaultWorkResult execute(final WorkerDaemonAction<T> action, final T spec, Operation parentWorkerOperation, BuildOperationExecutor.Operation parentBuildOperation) {
                BuildOperationWorkerRegistry.Completion workerLease = parentWorkerOperation.operationStart();
                BuildOperationDetails buildOperation = BuildOperationDetails.displayName(action.getDisplayName()).parent(parentBuildOperation).build();
                try {
                    return buildOperationExecutor.run(buildOperation, new Transformer<DefaultWorkResult, BuildOperationContext>() {
                        @Override
                        public DefaultWorkResult transform(BuildOperationContext buildOperationContext) {
                            return executeInWorkerClassLoader(action, spec, forkOptions);
                        }
                    });
                } finally {
                    workerLease.operationFinish();
                }
            }
        };
    }

    private <T extends WorkSpec> DefaultWorkResult executeInWorkerClassLoader(WorkerDaemonAction<T> action, T spec, DaemonForkOptions forkOptions) {
        ClassLoader actionClasspathLoader = createActionClasspathLoader(forkOptions);
        GroovySystemLoader actionClasspathGroovy = groovySystemLoaderFactory.forClassLoader(actionClasspathLoader);

        ClassLoader workerClassLoader = createWorkerClassLoader(actionClasspathLoader, forkOptions.getSharedPackages(), action.getClass());

        ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(workerClassLoader);
            Callable<?> worker = transferWorkerIntoWorkerClassloader(action, spec, workerClassLoader);
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

    private ClassLoader createWorkerClassLoader(ClassLoader actionClasspathLoader, Iterable<String> sharedPackages, Class<? extends WorkerDaemonAction> actionClass) {
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

    private <T extends WorkSpec> Callable<?> transferWorkerIntoWorkerClassloader(WorkerDaemonAction<T> action, T spec, ClassLoader workerClassLoader) throws IOException, ClassNotFoundException {
        byte[] serializedWorker = GUtil.serialize(new Worker<T>(action, spec));
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

    private static class Worker<T extends WorkSpec> implements Callable<Object>, Serializable {
        private final WorkerDaemonAction<T> workerAction;
        private final T spec;

        private Worker(WorkerDaemonAction<T> workerAction, T spec) {
            this.workerAction = workerAction;
            this.spec = spec;
        }

        @Override
        public Object call() throws Exception {
            return workerAction.execute(spec);
        }
    }
}
