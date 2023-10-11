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

import com.google.common.collect.Lists;
import org.gradle.api.Action;
import org.gradle.internal.Actions;
import org.gradle.internal.Cast;
import org.gradle.internal.classpath.CachedClasspathTransformer;
import org.gradle.internal.classpath.ClassPath;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.exceptions.NonGradleCauseExceptionsHolder;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.work.AbstractConditionalExecution;
import org.gradle.internal.work.AsyncWorkCompletion;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.ConditionalExecutionQueue;
import org.gradle.internal.work.DefaultConditionalExecutionQueue;
import org.gradle.internal.work.WorkerThreadRegistry;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.util.internal.CollectionUtils;
import org.gradle.workers.ClassLoaderWorkerSpec;
import org.gradle.workers.ProcessWorkerSpec;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.WorkerSpec;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RETAIN_PROJECT_LOCKS;

public class DefaultWorkerExecutor implements WorkerExecutor {
    private final ConditionalExecutionQueue<DefaultWorkResult> executionQueue;
    private final WorkerFactory daemonWorkerFactory;
    private final WorkerFactory isolatedClassloaderWorkerFactory;
    private final WorkerFactory noIsolationWorkerFactory;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final WorkerThreadRegistry workerThreadRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;
    private final WorkerDirectoryProvider workerDirectoryProvider;
    private final ClassLoaderStructureProvider classLoaderStructureProvider;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private final Instantiator instantiator;
    private final IsolationScheme<WorkAction<?>, WorkParameters> isolationScheme = new IsolationScheme<>(Cast.uncheckedCast(WorkAction.class), WorkParameters.class, WorkParameters.None.class);
    private final CachedClasspathTransformer classpathTransformer;
    private final File projectDir;
    private final File rootDir;

    public DefaultWorkerExecutor(
        WorkerFactory daemonWorkerFactory, WorkerFactory isolatedClassloaderWorkerFactory, WorkerFactory noIsolationWorkerFactory,
        JavaForkOptionsFactory forkOptionsFactory, WorkerThreadRegistry workerThreadRegistry, BuildOperationExecutor buildOperationExecutor,
        AsyncWorkTracker asyncWorkTracker, WorkerDirectoryProvider workerDirectoryProvider, WorkerExecutionQueueFactory workerExecutionQueueFactory,
        ClassLoaderStructureProvider classLoaderStructureProvider, ActionExecutionSpecFactory actionExecutionSpecFactory, Instantiator instantiator,
        CachedClasspathTransformer classpathTransformer,
        File projectDir,
        File rootDir
    ) {
        this.daemonWorkerFactory = daemonWorkerFactory;
        this.isolatedClassloaderWorkerFactory = isolatedClassloaderWorkerFactory;
        this.noIsolationWorkerFactory = noIsolationWorkerFactory;
        this.forkOptionsFactory = forkOptionsFactory;
        this.executionQueue = workerExecutionQueueFactory.create();
        this.workerThreadRegistry = workerThreadRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
        this.workerDirectoryProvider = workerDirectoryProvider;
        this.classLoaderStructureProvider = classLoaderStructureProvider;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
        this.instantiator = instantiator;
        this.classpathTransformer = classpathTransformer;
        this.projectDir = projectDir;
        this.rootDir = rootDir;
    }

    DefaultWorkerExecutor(
        WorkerFactory daemonWorkerFactory, WorkerFactory isolatedClassloaderWorkerFactory, WorkerFactory noIsolationWorkerFactory,
        JavaForkOptionsFactory forkOptionsFactory, WorkerThreadRegistry workerThreadRegistry, BuildOperationExecutor buildOperationExecutor,
        AsyncWorkTracker asyncWorkTracker, WorkerDirectoryProvider workerDirectoryProvider, WorkerExecutionQueueFactory workerExecutionQueueFactory,
        ClassLoaderStructureProvider classLoaderStructureProvider, ActionExecutionSpecFactory actionExecutionSpecFactory, Instantiator instantiator,
        CachedClasspathTransformer classpathTransformer,
        File projectDir
    ) {
        this(daemonWorkerFactory,  isolatedClassloaderWorkerFactory, noIsolationWorkerFactory, forkOptionsFactory, workerThreadRegistry, buildOperationExecutor, asyncWorkTracker, workerDirectoryProvider, workerExecutionQueueFactory, classLoaderStructureProvider, actionExecutionSpecFactory, instantiator, classpathTransformer, projectDir,
            /* TODO-RC: what to do about root directory? */ projectDir);
    }

    @Override
    public WorkQueue noIsolation() {
        return noIsolation(Actions.doNothing());
    }

    @Override
    public WorkQueue classLoaderIsolation() {
        return classLoaderIsolation(Actions.doNothing());
    }

    @Override
    public WorkQueue processIsolation() {
        return processIsolation(Actions.doNothing());
    }

    @Override
    public WorkQueue noIsolation(Action<? super WorkerSpec> action) {
        DefaultWorkerSpec spec = instantiator.newInstance(DefaultWorkerSpec.class);
        action.execute(spec);
        return instantiator.newInstance(DefaultWorkQueue.class, this, spec, noIsolationWorkerFactory);
    }

    @Override
    public WorkQueue classLoaderIsolation(Action<? super ClassLoaderWorkerSpec> action) {
        DefaultClassLoaderWorkerSpec spec = instantiator.newInstance(DefaultClassLoaderWorkerSpec.class);
        action.execute(spec);
        return instantiator.newInstance(DefaultWorkQueue.class, this, spec, isolatedClassloaderWorkerFactory);
    }

    @Override
    public WorkQueue processIsolation(Action<? super ProcessWorkerSpec> action) {
        DefaultProcessWorkerSpec spec = instantiator.newInstance(DefaultProcessWorkerSpec.class, forkOptionsFactory.newDecoratedJavaForkOptions());
        File defaultWorkingDir = spec.getForkOptions().getWorkingDir();
        File workingDirectory = workerDirectoryProvider.getWorkingDirectory();
        action.execute(spec);

        if (!defaultWorkingDir.equals(spec.getForkOptions().getWorkingDir())) {
            throw new IllegalArgumentException("Setting the working directory of a worker is not supported.");
        } else {
            spec.getForkOptions().setWorkingDir(workingDirectory);
        }

        return instantiator.newInstance(DefaultWorkQueue.class, this, spec, daemonWorkerFactory);
    }
    private <T extends WorkParameters> AsyncWorkCompletion submitWork(Class<? extends WorkAction<T>> workActionClass, Action<? super T> parameterAction, WorkerSpec workerSpec, WorkerFactory workerFactory) {
        Class<T> parameterType = isolationScheme.parameterTypeFor(workActionClass);
        T parameters = (parameterType == null) ? null : instantiator.newInstance(parameterType);
        if (parameters != null) {
            parameterAction.execute(parameters);
        }

        String description = workActionClass.getName();
        WorkerRequirement workerRequirement = getWorkerRequirement(workActionClass, workerSpec, parameters);
        IsolatedParametersActionExecutionSpec<?> spec;
        try {
            // Isolate parameters in this thread prior to starting work in a separate thread
            spec = actionExecutionSpecFactory.newIsolatedSpec(description, workActionClass, parameters, workerRequirement, false);
        } catch (Throwable t) {
            throw new WorkExecutionException(description, t);
        }

        return submitWork(spec, workerFactory, workerRequirement);
    }

    private AsyncWorkCompletion submitWork(IsolatedParametersActionExecutionSpec<?> spec, WorkerFactory workerFactory, WorkerRequirement workerRequirement) {
        checkIsManagedThread();
        final BuildOperationRef currentBuildOperation = buildOperationExecutor.getCurrentOperation();
        WorkItemExecution execution = new WorkItemExecution(spec.getDisplayName(), () -> {
            try {
                BuildOperationAwareWorker worker = workerFactory.getWorker(workerRequirement);
                return worker.execute(spec, currentBuildOperation);
            } catch (Throwable t) {
                throw new WorkExecutionException(spec.getDisplayName(), t);
            }
        });
        executionQueue.submit(execution);
        asyncWorkTracker.registerWork(currentBuildOperation, execution);
        return execution;
    }

    private void checkIsManagedThread() {
        if (!workerThreadRegistry.isWorkerThread()) {
            throw new IllegalStateException("An attempt was made to submit work from a thread not managed by Gradle.  Work may only be submitted from a Gradle-managed thread.");
        }
    }

    /**
     * Wait for any outstanding work to complete.  Note that if there is uncompleted work associated
     * with the current build operation, we'll also temporarily expand the thread pool of the execution queue.
     * This is to avoid a thread starvation scenario (see {@link DefaultConditionalExecutionQueue#expand()}
     * for further details).
     */
    @Override
    public void await() throws WorkerExecutionException {
        BuildOperationRef currentOperation = buildOperationExecutor.getCurrentOperation();
        try {
            if (asyncWorkTracker.hasUncompletedWork(currentOperation)) {
                executionQueue.expand();
            }
            asyncWorkTracker.waitForCompletion(currentOperation, RETAIN_PROJECT_LOCKS);
        } catch (DefaultMultiCauseException e) {
            throw workerExecutionException(e.getCauses());
        }
    }

    private void await(List<AsyncWorkCompletion> workItems) throws WorkExecutionException {
        BuildOperationRef currentOperation = buildOperationExecutor.getCurrentOperation();
        try {
            if (CollectionUtils.any(workItems, workItem -> !workItem.isComplete())) {
                executionQueue.expand();
            }
            asyncWorkTracker.waitForCompletion(currentOperation, workItems, RETAIN_PROJECT_LOCKS);
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

    WorkerRequirement getWorkerRequirement(Class<?> executionClass, WorkerSpec configuration, WorkParameters parameters) {
        if (configuration instanceof ProcessWorkerSpec) {
            DaemonForkOptionsBuilder builder = new DaemonForkOptionsBuilder(forkOptionsFactory)
                .keepAliveMode(KeepAliveMode.SESSION);
            ProcessWorkerSpec processConfiguration = (ProcessWorkerSpec) configuration;
            JavaForkOptions forkOptions = forkOptionsFactory.newJavaForkOptions();
            processConfiguration.getForkOptions().copyTo(forkOptions);
            forkOptions.setWorkingDir(workerDirectoryProvider.getWorkingDirectory());

            ClassPath isolatedFromChanges = classpathTransformer.transform(DefaultClassPath.of(processConfiguration.getClasspath()), CachedClasspathTransformer.StandardTransform.None);
            builder.javaForkOptions(forkOptions)
                .withClassLoaderStructure(classLoaderStructureProvider.getWorkerProcessClassLoaderStructure(isolatedFromChanges.getAsFiles(), getParamClasses(executionClass, parameters)));

            return new ForkedWorkerRequirement(projectDir, rootDir, builder.build());
        } else if (configuration instanceof ClassLoaderWorkerSpec) {
            ClassLoaderWorkerSpec classLoaderConfiguration = (ClassLoaderWorkerSpec) configuration;
            ClassPath isolatedFromChanges = classpathTransformer.transform(DefaultClassPath.of(classLoaderConfiguration.getClasspath()), CachedClasspathTransformer.StandardTransform.None);
            return new IsolatedClassLoaderWorkerRequirement(projectDir, rootDir, classLoaderStructureProvider.getInProcessClassLoaderStructure(isolatedFromChanges.getAsFiles(), getParamClasses(executionClass, parameters)));
        } else {
            return new FixedClassLoaderWorkerRequirement(projectDir, rootDir, Thread.currentThread().getContextClassLoader());
        }
    }

    private Class<?>[] getParamClasses(Class<?> actionClass, WorkParameters parameters) {
        if (parameters != null) {
            return new Class<?>[]{actionClass, parameters.getClass()};
        }
        return new Class<?>[]{actionClass};
    }

    @Contextual
    private static class WorkExecutionException extends RuntimeException implements NonGradleCauseExceptionsHolder {
        WorkExecutionException(String description, Throwable cause) {
            super(toMessage(description), cause);
        }

        private static String toMessage(String description) {
            return "A failure occurred while executing " + description;
        }

        @Override
        public boolean hasCause(Class<?> type) {
            return type.isInstance(getCause());
        }
    }

    private static class WorkItemExecution extends AbstractConditionalExecution<DefaultWorkResult> implements AsyncWorkCompletion {
        private final String description;

        public WorkItemExecution(String description, Callable<DefaultWorkResult> callable) {
            super(callable);
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

    @NotThreadSafe
    static class DefaultWorkQueue implements WorkQueue {
        private final DefaultWorkerExecutor workerExecutor;
        private final WorkerSpec spec;
        private final WorkerFactory workerFactory;
        private final List<AsyncWorkCompletion> workItems = Lists.newArrayList();

        public DefaultWorkQueue(DefaultWorkerExecutor workerExecutor, WorkerSpec spec, WorkerFactory workerFactory) {
            this.workerExecutor = workerExecutor;
            this.spec = spec;
            this.workerFactory = workerFactory;
        }

        @Override
        public <T extends WorkParameters> void submit(Class<? extends WorkAction<T>> workActionClass, Action<? super T> parameterAction) {
            workItems.add(workerExecutor.submitWork(workActionClass, parameterAction, spec, workerFactory));
        }

        @Override
        public void await() throws WorkerExecutionException {
            workerExecutor.await(workItems);
        }
    }
}
