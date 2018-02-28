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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.work.AbstractConditionalExecution;
import org.gradle.internal.work.AsyncWorkCompletion;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.ConditionalExecutionQueue;
import org.gradle.internal.work.ConditionalExecutionQueueFactory;
import org.gradle.internal.work.NoAvailableWorkerLeaseException;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.util.CollectionUtils;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

public class DefaultWorkerExecutor implements WorkerExecutor, Stoppable {
    private final ConditionalExecutionQueue<DefaultWorkResult> executionQueue;
    private final WorkerFactory daemonWorkerFactory;
    private final WorkerFactory isolatedClassloaderWorkerFactory;
    private final WorkerFactory noIsolationWorkerFactory;
    private final FileResolver fileResolver;
    private final WorkerLeaseRegistry workerLeaseRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;
    private final WorkerDirectoryProvider workerDirectoryProvider;

    public DefaultWorkerExecutor(WorkerFactory daemonWorkerFactory, WorkerFactory isolatedClassloaderWorkerFactory, WorkerFactory noIsolationWorkerFactory,
                                 FileResolver fileResolver, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor,
                                 AsyncWorkTracker asyncWorkTracker, WorkerDirectoryProvider workerDirectoryProvider, ConditionalExecutionQueueFactory conditionalExecutionQueueFactory) {
        this.daemonWorkerFactory = daemonWorkerFactory;
        this.isolatedClassloaderWorkerFactory = isolatedClassloaderWorkerFactory;
        this.noIsolationWorkerFactory = noIsolationWorkerFactory;
        this.fileResolver = fileResolver;
        this.executionQueue = conditionalExecutionQueueFactory.create("WorkerExecutor Queue", DefaultWorkResult.class);
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
        this.workerDirectoryProvider = workerDirectoryProvider;
    }

    @Override
    public void stop() {
        executionQueue.stop();
    }

    @Override
    public void submit(Class<? extends Runnable> actionClass, Action<? super WorkerConfiguration> configAction) {
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(fileResolver);
        configAction.execute(configuration);
        String description = configuration.getDisplayName() != null ? configuration.getDisplayName() : actionClass.getName();

        // Serialize parameters in this thread prior to starting work in a separate thread
        ActionExecutionSpec spec;
        try {
            spec = new SerializingActionExecutionSpec(actionClass, description, configuration.getForkOptions().getWorkingDir(), configuration.getParams());
        } catch (Throwable t) {
            throw new WorkExecutionException(description, t);
        }

        submit(spec, configuration.getIsolationMode(), getDaemonForkOptions(actionClass, configuration));
    }

    private void submit(final ActionExecutionSpec spec, final IsolationMode isolationMode, final DaemonForkOptions daemonForkOptions) {
        final WorkerLease currentWorkerWorkerLease = getCurrentWorkerLease();
        final BuildOperationRef currentBuildOperation = buildOperationExecutor.getCurrentOperation();
        WorkerExecution execution = new WorkerExecution(spec.getDisplayName(), currentWorkerWorkerLease, new Callable<DefaultWorkResult>() {
            @Override
            public DefaultWorkResult call() throws Exception {
                try {
                    WorkerFactory workerFactory = getWorkerFactory(isolationMode);
                    Worker worker = workerFactory.getWorker(daemonForkOptions);
                    return worker.execute(spec, currentBuildOperation);
                } catch (Throwable t) {
                    throw new WorkExecutionException(spec.getDisplayName(), t);
                }
            }
        });
        executionQueue.submit(execution);
        asyncWorkTracker.registerWork(currentBuildOperation, execution);
    }

    private WorkerLease getCurrentWorkerLease() {
        try {
            return workerLeaseRegistry.getCurrentWorkerLease();
        } catch (NoAvailableWorkerLeaseException e) {
            throw new IllegalStateException("An attempt was made to submit work from a thread not managed by Gradle.  Work may only be submitted from a Gradle-managed thread.", e);
        }
    }

    private WorkerFactory getWorkerFactory(IsolationMode isolationMode) {
        switch(isolationMode) {
            case AUTO:
            case CLASSLOADER:
                return isolatedClassloaderWorkerFactory;
            case NONE:
                return noIsolationWorkerFactory;
            case PROCESS:
                return daemonWorkerFactory;
            default:
                throw new IllegalArgumentException("Unknown isolation mode: " + isolationMode);
        }
    }

    @Override
    public void await() throws WorkerExecutionException {
        BuildOperationRef currentOperation = buildOperationExecutor.getCurrentOperation();
        try {
            asyncWorkTracker.waitForCompletion(currentOperation, false);
        } catch (DefaultMultiCauseException e) {
            throw workerExecutionException(e.getCauses());
        }
    }

    private WorkerExecutionException workerExecutionException(List<? extends Throwable> failures) {
        if (failures.size() == 1) {
            throw new WorkerExecutionException("There was a failure while executing work items", failures);
        } else {
            throw new WorkerExecutionException("There were multiple failures while executing work items", failures);
        }
    }

    DaemonForkOptions getDaemonForkOptions(Class<?> actionClass, WorkerConfiguration configuration) {
        validateWorkerConfiguration(configuration);
        Iterable<Class<?>> paramTypes = CollectionUtils.collect(configuration.getParams(), new Transformer<Class<?>, Object>() {
            @Override
            public Class<?> transform(Object o) {
                return o.getClass();
            }
        });
        return toDaemonOptions(actionClass, paramTypes, configuration.getForkOptions(), configuration.getClasspath());
    }

    private void validateWorkerConfiguration(WorkerConfiguration configuration) {
        if (configuration.getIsolationMode() == IsolationMode.NONE) {
            if (configuration.getClasspath().iterator().hasNext()) {
                throw unsupportedWorkerConfigurationException("classpath", configuration.getIsolationMode());
            }
        }

        if (configuration.getIsolationMode() == IsolationMode.NONE || configuration.getIsolationMode() == IsolationMode.CLASSLOADER) {
            if (!configuration.getForkOptions().getBootstrapClasspath().isEmpty()) {
                throw unsupportedWorkerConfigurationException("bootstrap classpath", configuration.getIsolationMode());
            }

            if (!configuration.getForkOptions().getJvmArgs().isEmpty()) {
                throw unsupportedWorkerConfigurationException("jvm arguments", configuration.getIsolationMode());
            }

            if (configuration.getForkOptions().getMaxHeapSize() != null) {
                throw unsupportedWorkerConfigurationException("maximum heap size", configuration.getIsolationMode());
            }

            if (configuration.getForkOptions().getMinHeapSize() != null) {
                throw unsupportedWorkerConfigurationException("minimum heap size", configuration.getIsolationMode());
            }

            if (!configuration.getForkOptions().getSystemProperties().isEmpty()) {
                throw unsupportedWorkerConfigurationException("system properties", configuration.getIsolationMode());
            }
        }
    }

    private RuntimeException unsupportedWorkerConfigurationException(String propertyDescription, IsolationMode isolationMode) {
        return new UnsupportedOperationException("The worker " + propertyDescription + " cannot be set when using isolation mode " + isolationMode.name());
    }

    private DaemonForkOptions toDaemonOptions(Class<?> actionClass, Iterable<Class<?>> paramClasses, JavaForkOptions userForkOptions, Iterable<File> classpath) {
        ImmutableSet.Builder<File> classpathBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> sharedPackagesBuilder = ImmutableSet.builder();

        sharedPackagesBuilder.add("javax.inject");

        if (classpath != null) {
            classpathBuilder.addAll(classpath);
        }

        addVisibilityFor(actionClass, classpathBuilder, sharedPackagesBuilder, true);

        for (Class<?> paramClass : paramClasses) {
            addVisibilityFor(paramClass, classpathBuilder, sharedPackagesBuilder, false);
        }

        Iterable<File> daemonClasspath = classpathBuilder.build();
        Iterable<String> daemonSharedPackages = sharedPackagesBuilder.build();

        JavaForkOptions forkOptions = new DefaultJavaForkOptions(fileResolver);
        userForkOptions.copyTo(forkOptions);
        forkOptions.setWorkingDir(workerDirectoryProvider.getIdleWorkingDirectory());

        return new DaemonForkOptionsBuilder(fileResolver)
                        .javaForkOptions(forkOptions)
                        .classpath(daemonClasspath)
                        .sharedPackages(daemonSharedPackages)
                        .keepAliveMode(KeepAliveMode.DAEMON)
                        .build();
    }

    private static void addVisibilityFor(Class<?> visibleClass, ImmutableSet.Builder<File> classpathBuilder, ImmutableSet.Builder<String> sharedPackagesBuilder, boolean addToSharedPackages) {
        if (visibleClass.getClassLoader() != null) {
            classpathBuilder.addAll(ClasspathUtil.getClasspath(visibleClass.getClassLoader()).getAsFiles());
        }

        if (addToSharedPackages) {
            addVisiblePackage(visibleClass, sharedPackagesBuilder);
        }
    }

    private static void addVisiblePackage(Class<?> visibleClass, ImmutableSet.Builder<String> sharedPackagesBuilder) {
        if (visibleClass.getPackage() == null || "".equals(visibleClass.getPackage().getName())) {
            sharedPackagesBuilder.add(FilteringClassLoader.DEFAULT_PACKAGE);
        } else {
            sharedPackagesBuilder.add(visibleClass.getPackage().getName());
        }
    }

    @Contextual
    private static class WorkExecutionException extends RuntimeException {
        WorkExecutionException(String description, Throwable cause) {
            super("A failure occurred while executing " + description, cause);
        }
    }

    private static class WorkerExecution extends AbstractConditionalExecution<DefaultWorkResult> implements AsyncWorkCompletion {
        private final String description;

        public WorkerExecution(String description, WorkerLease parentWorkerLease, Callable<DefaultWorkResult> callable) {
            super(callable, new LazyChildWorkerLeaseLock(parentWorkerLease));
            this.description = description;
        }

        @Override
        public void waitForCompletion() {
            DefaultWorkResult result = await();
            if (!result.isSuccess()) {
                throw new WorkExecutionException(description, result.getException());
            }
        }
    }

    private static class LazyChildWorkerLeaseLock implements ResourceLock {
        private final WorkerLease parentWorkerLease;
        private WorkerLease child;

        public LazyChildWorkerLeaseLock(WorkerLease parentWorkerLease) {
            this.parentWorkerLease = parentWorkerLease;
        }

        @Override
        public boolean isLocked() {
            return getChild().isLocked();
        }

        @Override
        public boolean isLockedByCurrentThread() {
            return getChild().isLockedByCurrentThread();
        }

        @Override
        public boolean tryLock() {
            return getChild().tryLock();
        }

        @Override
        public void unlock() {
            getChild().unlock();
        }

        @Override
        public String getDisplayName() {
            return getChild().getDisplayName();
        }

        private WorkerLease getChild() {
            if (child == null) {
                child = parentWorkerLease.createChild();
            }
            return child;
        }
    }
}
