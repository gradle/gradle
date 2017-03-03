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
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.operations.BuildOperationWorkerRegistry;
import org.gradle.internal.operations.BuildOperationWorkerRegistry.Operation;
import org.gradle.internal.progress.BuildOperationExecutor;
import org.gradle.internal.work.AsyncWorkCompletion;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.process.JavaForkOptions;
import org.gradle.util.CollectionUtils;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class DefaultWorkerExecutor implements WorkerExecutor {
    private final ListeningExecutorService executor;
    private final WorkerDaemonFactory workerDaemonFactory;
    private final Class<? extends WorkerDaemonProtocol> serverImplementationClass;
    private final FileResolver fileResolver;
    private final BuildOperationWorkerRegistry buildOperationWorkerRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;

    public DefaultWorkerExecutor(WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver, Class<? extends WorkerDaemonProtocol> serverImplementationClass, ExecutorFactory executorFactory, BuildOperationWorkerRegistry buildOperationWorkerRegistry, BuildOperationExecutor buildOperationExecutor, AsyncWorkTracker asyncWorkTracker) {
        this.workerDaemonFactory = workerDaemonFactory;
        this.fileResolver = fileResolver;
        this.serverImplementationClass = serverImplementationClass;
        this.executor = MoreExecutors.listeningDecorator(executorFactory.create("Worker Daemon Execution"));
        this.buildOperationWorkerRegistry = buildOperationWorkerRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
    }

    @Override
    public void submit(Class<? extends Runnable> actionClass, Action<WorkerConfiguration> configAction) {
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(fileResolver);
        configAction.execute(configuration);
        WorkSpec spec = new ParamSpec(configuration.getParams());
        String description = configuration.getDisplayName() != null ? configuration.getDisplayName() : actionClass.getName();
        WorkerDaemonAction action = new WorkerDaemonRunnableAction(description, actionClass);
        submit(action, spec, configuration.getForkOptions().getWorkingDir(), getDaemonForkOptions(actionClass, configuration));
    }

    private void submit(final WorkerDaemonAction action, final WorkSpec spec, final File workingDir, final DaemonForkOptions daemonForkOptions) {
        final Operation currentWorkerOperation = buildOperationWorkerRegistry.getCurrent();
        final BuildOperationExecutor.Operation currentBuildOperation = buildOperationExecutor.getCurrentOperation();
        ListenableFuture<DefaultWorkResult> workerDaemonResult = executor.submit(new Callable<DefaultWorkResult>() {
            @Override
            public DefaultWorkResult call() throws Exception {
                try {
                    WorkerDaemon daemon = workerDaemonFactory.getDaemon(serverImplementationClass, workingDir, daemonForkOptions);
                    return daemon.execute(action, spec, currentWorkerOperation, currentBuildOperation);
                } catch (Throwable t) {
                    throw new WorkExecutionException(action.getDescription(), t);
                }
            }
        });
        registerAsyncWork(action.getDescription(), workerDaemonResult);
    }

    void registerAsyncWork(final String description, final Future<DefaultWorkResult> workItem) {
        asyncWorkTracker.registerWork(buildOperationExecutor.getCurrentOperation(), new AsyncWorkCompletion() {
            @Override
            public void waitForCompletion() {
                try {
                    DefaultWorkResult result = workItem.get();
                    if (!result.isSuccess()) {
                        throw new WorkExecutionException(description, result.getException());
                    }
                } catch (InterruptedException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                } catch (ExecutionException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
            }
        });
    }

    @Override
    public void await() throws WorkerExecutionException {
        BuildOperationExecutor.Operation currentOperation = buildOperationExecutor.getCurrentOperation();
        try {
            asyncWorkTracker.waitForCompletion(currentOperation);
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
        Iterable<Class<?>> paramTypes = CollectionUtils.collect(configuration.getParams(), new Transformer<Class<?>, Object>() {
            @Override
            public Class<?> transform(Object o) {
                return o.getClass();
            }
        });
        return toDaemonOptions(actionClass, paramTypes, configuration.getForkOptions(), configuration.getClasspath());
    }

    private DaemonForkOptions toDaemonOptions(Class<?> actionClass, Iterable<Class<?>> paramClasses, JavaForkOptions forkOptions, Iterable<File> classpath) {
        ImmutableSet.Builder<File> classpathBuilder = ImmutableSet.builder();
        ImmutableSet.Builder<String> sharedPackagesBuilder = ImmutableSet.builder();

        if (classpath != null) {
            classpathBuilder.addAll(classpath);
        }

        addVisibilityFor(actionClass, classpathBuilder, sharedPackagesBuilder);

        for (Class<?> paramClass : paramClasses) {
            addVisibilityFor(paramClass, classpathBuilder, sharedPackagesBuilder);
        }

        Iterable<File> daemonClasspath = classpathBuilder.build();
        Iterable<String> daemonSharedPackages = sharedPackagesBuilder.build();

        return new DaemonForkOptions(forkOptions.getMinHeapSize(), forkOptions.getMaxHeapSize(), forkOptions.getAllJvmArgs(), daemonClasspath, daemonSharedPackages);
    }

    private static void addVisibilityFor(Class<?> visibleClass, ImmutableSet.Builder<File> classpathBuilder, ImmutableSet.Builder<String> sharedPackagesBuilder) {
        if (visibleClass.getClassLoader() != null) {
            classpathBuilder.addAll(ClasspathUtil.getClasspath(visibleClass.getClassLoader()).getAsFiles());
        }

        if (visibleClass.getPackage() == null || "".equals(visibleClass.getPackage().getName())) {
            sharedPackagesBuilder.add(FilteringClassLoader.DEFAULT_PACKAGE);
        } else {
            sharedPackagesBuilder.add(visibleClass.getPackage().getName());
        }
    }

    @Contextual
    private static class WorkExecutionException extends RuntimeException {
        public WorkExecutionException(String description) {
            this(description, null);
        }

        public WorkExecutionException(String description, Throwable cause) {
            super("A failure occurred while executing " + description, cause);
        }
    }
}
