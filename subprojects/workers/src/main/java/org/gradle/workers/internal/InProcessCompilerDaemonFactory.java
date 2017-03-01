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
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.CachingClassLoader;
import org.gradle.internal.classloader.ClassLoaderFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.MultiParentClassLoader;
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
    private static final Logger LOGGER = Logging.getLogger(InProcessCompilerDaemonFactory.class);

    private final ClassLoaderFactory classLoaderFactory;
    private final BuildOperationWorkerRegistry buildOperationWorkerRegistry;
    private final BuildOperationExecutor buildOperationExecutor;

    public InProcessCompilerDaemonFactory(ClassLoaderFactory classLoaderFactory, BuildOperationWorkerRegistry buildOperationWorkerRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.classLoaderFactory = classLoaderFactory;
        this.buildOperationWorkerRegistry = buildOperationWorkerRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public WorkerDaemon getDaemon(final Class<? extends WorkerDaemonProtocol> serverImplementationClass, File workingDir, final DaemonForkOptions forkOptions) {
        return new WorkerDaemon() {
            @Override
            public <T extends WorkSpec> DefaultWorkResult execute(WorkerDaemonAction<T> action, T spec) {
                return execute(action, spec, buildOperationWorkerRegistry.getCurrent(), buildOperationExecutor.getCurrentOperation());
            }

            @Override
            public <T extends WorkSpec> DefaultWorkResult execute(final WorkerDaemonAction<T> action, final T spec, Operation parentWorkerOperation, BuildOperationExecutor.Operation parentBuildOperation) {
                BuildOperationWorkerRegistry.Completion workerLease = parentWorkerOperation.operationStart();
                BuildOperationDetails buildOperation = BuildOperationDetails.displayName(action.getDescription()).parent(parentBuildOperation).build();
                try {
                    return buildOperationExecutor.run(buildOperation, new Transformer<DefaultWorkResult, BuildOperationContext>() {
                        @Override
                        public DefaultWorkResult transform(BuildOperationContext buildOperationContext) {
                            return executeInWorkerClassLoader(serverImplementationClass, action, spec, forkOptions.getClasspath(), forkOptions.getSharedPackages());
                        }
                    });
                } finally {
                    workerLease.operationFinish();
                }
            }
        };
    }

    private <T extends WorkSpec> DefaultWorkResult executeInWorkerClassLoader(Class<? extends WorkerDaemonProtocol> serverImplementationClass, WorkerDaemonAction<T> action, T spec, Iterable<File> classpath, Iterable<String> sharedPackages) {
        ClassLoader workerClassLoader = createWorkerClassLoader(serverImplementationClass, classpath, sharedPackages);
        try {
            LOGGER.info("Executing {} in in-process worker.", action.getDescription());
            Callable<?> worker = transferWorkerIntoWorkerClassloader(action, spec, workerClassLoader);
            Object result = worker.call();
            DefaultWorkResult workResult = transferResultFromWorkerClassLoader(result);
            LOGGER.info("Successfully executed {} in in-process worker.", action.getDescription());
            return workResult;
        } catch (Exception e) {
            LOGGER.info("Exception executing {} in in-process worker: {}.", action.getDescription(), e);
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private ClassLoader createWorkerClassLoader(Class<? extends WorkerDaemonProtocol> serverImplementationClass, Iterable<File> classpath, Iterable<String> sharedPackages) {
        ClassLoader isolatedWorkerClassLoader = classLoaderFactory.createIsolatedClassLoader(new DefaultClassPath(classpath));
        FilteringClassLoader.Spec isolatedFilteringSpec = new FilteringClassLoader.Spec();
        for (String sharedPackage : sharedPackages) {
            isolatedFilteringSpec.allowPackage(sharedPackage);
        }
        ClassLoader filteredWorkerClassLoader = classLoaderFactory.createFilteringClassLoader(isolatedWorkerClassLoader, isolatedFilteringSpec);
        ClassLoader workerServerClassLoader = serverImplementationClass.getClassLoader();
        return new CachingClassLoader(new MultiParentClassLoader(filteredWorkerClassLoader, workerServerClassLoader));
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
