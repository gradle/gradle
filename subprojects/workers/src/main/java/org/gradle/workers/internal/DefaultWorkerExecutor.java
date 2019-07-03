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
import com.google.common.collect.Lists;
import com.google.common.reflect.TypeToken;
import org.gradle.api.Action;
import org.gradle.internal.Cast;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.exceptions.Contextual;
import org.gradle.internal.exceptions.DefaultMultiCauseException;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.resources.ResourceLock;
import org.gradle.internal.work.AbstractConditionalExecution;
import org.gradle.internal.work.AsyncWorkCompletion;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.ConditionalExecutionQueue;
import org.gradle.internal.work.DefaultConditionalExecutionQueue;
import org.gradle.internal.work.NoAvailableWorkerLeaseException;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.internal.work.WorkerLeaseRegistry.WorkerLease;
import org.gradle.model.internal.type.ModelType;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.internal.JavaForkOptionsFactory;
import org.gradle.process.internal.worker.child.WorkerDirectoryProvider;
import org.gradle.workers.BaseWorkerSpec;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecution;
import org.gradle.workers.WorkerExecutionException;
import org.gradle.workers.WorkerExecutor;
import org.gradle.workers.WorkerParameters;
import org.gradle.workers.WorkerSpec;

import java.io.File;
import java.lang.reflect.ParameterizedType;
import java.util.List;
import java.util.concurrent.Callable;

import static org.gradle.internal.classloader.ClassLoaderUtils.classFromContextLoader;
import static org.gradle.internal.work.AsyncWorkTracker.ProjectLockRetention.RETAIN_PROJECT_LOCKS;

public class DefaultWorkerExecutor implements WorkerExecutor {
    private final ConditionalExecutionQueue<DefaultWorkResult> executionQueue;
    private final WorkerFactory daemonWorkerFactory;
    private final WorkerFactory isolatedClassloaderWorkerFactory;
    private final WorkerFactory noIsolationWorkerFactory;
    private final JavaForkOptionsFactory forkOptionsFactory;
    private final WorkerLeaseRegistry workerLeaseRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker asyncWorkTracker;
    private final WorkerDirectoryProvider workerDirectoryProvider;
    private final ClassLoaderStructureProvider classLoaderStructureProvider;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private final Instantiator instantiator;

    public DefaultWorkerExecutor(WorkerFactory daemonWorkerFactory, WorkerFactory isolatedClassloaderWorkerFactory, WorkerFactory noIsolationWorkerFactory, JavaForkOptionsFactory forkOptionsFactory, WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor, AsyncWorkTracker asyncWorkTracker, WorkerDirectoryProvider workerDirectoryProvider, WorkerExecutionQueueFactory workerExecutionQueueFactory, ClassLoaderStructureProvider classLoaderStructureProvider, ActionExecutionSpecFactory actionExecutionSpecFactory, Instantiator instantiator) {
        this.daemonWorkerFactory = daemonWorkerFactory;
        this.isolatedClassloaderWorkerFactory = isolatedClassloaderWorkerFactory;
        this.noIsolationWorkerFactory = noIsolationWorkerFactory;
        this.forkOptionsFactory = forkOptionsFactory;
        this.executionQueue = workerExecutionQueueFactory.create();
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.asyncWorkTracker = asyncWorkTracker;
        this.workerDirectoryProvider = workerDirectoryProvider;
        this.classLoaderStructureProvider = classLoaderStructureProvider;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
        this.instantiator = instantiator;
    }

    @Override
    public void submit(Class<? extends Runnable> actionClass, Action<? super WorkerConfiguration> configAction) {
        WorkerConfiguration configuration = new DefaultWorkerConfiguration(forkOptionsFactory);
        configAction.execute(configuration);

        Action<WorkerSpec<AdapterWorkerParameters>> action = new Action<WorkerSpec<AdapterWorkerParameters>>() {
            @Override
            public void execute(WorkerSpec<AdapterWorkerParameters> adapterWorkerParametersWorkerSpec) {
                AdapterWorkerParameters parameters = adapterWorkerParametersWorkerSpec.getParameters();
                parameters.setImplementationClassName(actionClass.getName());
                parameters.setParams(configuration.getParams());

                adapterWorkerParametersWorkerSpec.classpath(configuration.getClasspath());
                adapterWorkerParametersWorkerSpec.setIsolationMode(configuration.getIsolationMode());
                adapterWorkerParametersWorkerSpec.setDisplayName(configuration.getDisplayName());

                configuration.getForkOptions().copyTo(adapterWorkerParametersWorkerSpec.getForkOptions());
            }
        };
        execute(AdapterWorkerExecution.class, action);
    }

    @Override
    public <T extends WorkerParameters> void execute(Class<? extends WorkerExecution<T>> workerExecutionClass, Action<? super WorkerSpec<T>> configAction) {
        ParameterizedType superType = (ParameterizedType) TypeToken.of(workerExecutionClass).getSupertype(WorkerExecution.class).getType();
        Class<T> parameterType = Cast.uncheckedNonnullCast(TypeToken.of(superType.getActualTypeArguments()[0]).getRawType());
        if (parameterType == WorkerParameters.class) {
            throw new IllegalArgumentException(String.format("Could not create worker parameters: must use a sub-type of %s as parameter type. Use %s for executions without parameters.", ModelType.of(WorkerParameters.class).getDisplayName(), ModelType.of(WorkerParameters.None.class).getDisplayName()));
        }
        T parameters = (parameterType == WorkerParameters.None.class) ? null : instantiator.newInstance(parameterType);
        WorkerSpec<T> workerSpec = new DefaultWorkerSpec<T>(forkOptionsFactory, parameters);
        File defaultWorkingDir = workerSpec.getForkOptions().getWorkingDir();
        File workingDirectory = workerDirectoryProvider.getWorkingDirectory();
        configAction.execute(workerSpec);
        String description = getWorkerDisplayName(workerSpec, workerExecutionClass);

        if (!defaultWorkingDir.equals(workerSpec.getForkOptions().getWorkingDir())) {
            throw new WorkExecutionException(description + ": setting the working directory of a worker is not supported.");
        } else {
            workerSpec.getForkOptions().setWorkingDir(workingDirectory);
        }

        ActionExecutionSpec spec;
        DaemonForkOptions forkOptions = getDaemonForkOptions(workerExecutionClass, workerSpec);
        try {
            // Isolate parameters in this thread prior to starting work in a separate thread
            spec = actionExecutionSpecFactory.newIsolatedSpec(description, workerExecutionClass, workerSpec.getParameters(), forkOptions.getClassLoaderStructure());
        } catch (Throwable t) {
            throw new WorkExecutionException(description, t);
        }

        execute(spec, workerSpec.getIsolationMode(), forkOptions);
    }

    private void execute(final ActionExecutionSpec spec, final IsolationMode isolationMode, final DaemonForkOptions daemonForkOptions) {
        final WorkerLease currentWorkerWorkerLease = getCurrentWorkerLease();
        final BuildOperationRef currentBuildOperation = buildOperationExecutor.getCurrentOperation();
        WorkItemExecution execution = new WorkItemExecution(spec.getDisplayName(), currentWorkerWorkerLease, new Callable<DefaultWorkResult>() {
            @Override
            public DefaultWorkResult call() throws Exception {
                try {
                    WorkerFactory workerFactory = getWorkerFactory(isolationMode);
                    BuildOperationAwareWorker worker = workerFactory.getWorker(daemonForkOptions);
                    return worker.execute(spec, currentBuildOperation);
                } catch (Throwable t) {
                    throw new WorkExecutionException(spec.getDisplayName(), t);
                }
            }
        });
        executionQueue.submit(execution);
        asyncWorkTracker.registerWork(currentBuildOperation, execution);
    }

    private static String getWorkerDisplayName(WorkerSpec<?> workerSpec, Class<?> workerExecutionClass) {
        if (workerSpec.getDisplayName() != null) {
            return workerSpec.getDisplayName();
        }

        if (workerExecutionClass == AdapterWorkerExecution.class) {
            AdapterWorkerParameters parameters = (AdapterWorkerParameters) workerSpec.getParameters();
            return parameters.getImplementationClassName();
        } else {
            return workerExecutionClass.getName();
        }
    }

    private WorkerLease getCurrentWorkerLease() {
        try {
            return workerLeaseRegistry.getCurrentWorkerLease();
        } catch (NoAvailableWorkerLeaseException e) {
            throw new IllegalStateException("An attempt was made to submit work from a thread not managed by Gradle.  Work may only be submitted from a Gradle-managed thread.", e);
        }
    }

    private WorkerFactory getWorkerFactory(IsolationMode isolationMode) {
        switch (isolationMode) {
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

    /**
     * Wait for any outstanding work to complete.  Note that if there is uncompleted work associated
     * with the current build operation, we'll also temporarily expand the thread pool of the execution queue.
     * This is to avoid a thread starvation scenario (see {@link DefaultConditionalExecutionQueue#expand(boolean)}
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

    private WorkerExecutionException workerExecutionException(List<? extends Throwable> failures) {
        if (failures.size() == 1) {
            throw new WorkerExecutionException("There was a failure while executing work items", failures);
        } else {
            throw new WorkerExecutionException("There were multiple failures while executing work items", failures);
        }
    }

    DaemonForkOptions getDaemonForkOptions(Class<?> executionClass, WorkerSpec configuration) {
        validateWorkerConfiguration(configuration);
        Class<?> actionClass;
        Object[] params;
        if (configuration.getParameters() instanceof AdapterWorkerParameters) {
            AdapterWorkerParameters adapterWorkerParameters = (AdapterWorkerParameters) configuration.getParameters();
            actionClass = classFromContextLoader(adapterWorkerParameters.getImplementationClassName());
            params = adapterWorkerParameters.getParams();
        } else {
            actionClass = executionClass;
            params = new Object[] {configuration.getParameters()};
        }
        return toDaemonOptions(getParamClasses(actionClass, params), configuration.getForkOptions(), configuration.getClasspath(), configuration.getIsolationMode());
    }

    private void validateWorkerConfiguration(BaseWorkerSpec configuration) {
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

    private DaemonForkOptions toDaemonOptions(Class<?>[] visibleClasses, JavaForkOptions userForkOptions, Iterable<File> additionalClasspath, IsolationMode isolationMode) {
        JavaForkOptions forkOptions = forkOptionsFactory.newJavaForkOptions();
        userForkOptions.copyTo(forkOptions);
        forkOptions.setWorkingDir(workerDirectoryProvider.getWorkingDirectory());

        DaemonForkOptionsBuilder builder = new DaemonForkOptionsBuilder(forkOptionsFactory)
            .javaForkOptions(forkOptions)
            .keepAliveMode(KeepAliveMode.DAEMON);

        if (isolationMode != IsolationMode.NONE) {
            if (isolationMode == IsolationMode.PROCESS) {
                builder.withClassLoaderStructure(classLoaderStructureProvider.getWorkerProcessClassLoaderStructure(additionalClasspath, visibleClasses));
            } else {
                builder.withClassLoaderStructure(classLoaderStructureProvider.getInProcessClassLoaderStructure(additionalClasspath, visibleClasses));
            }
        }

        return builder.build();
    }

    private Class<?>[] getParamClasses(Class<?> actionClass, Object[] params) {
        List<Class<?>> classes = Lists.newArrayList();
        classes.add(actionClass);
        for (Object param : params) {
            if (param != null) {
                classes.add(param.getClass());
            }
        }
        return classes.toArray(new Class[0]);
    }

    private static void addVisibilityFor(Class<?> visibleClass, ImmutableSet.Builder<File> classpathBuilder) {
        if (visibleClass.getClassLoader() != null) {
            classpathBuilder.addAll(ClasspathUtil.getClasspath(visibleClass.getClassLoader()).getAsFiles());
        }
    }

    @Contextual
    private static class WorkExecutionException extends RuntimeException {
        WorkExecutionException(String description) {
            super(toMessage(description));
        }

        WorkExecutionException(String description, Throwable cause) {
            super(toMessage(description), cause);
        }

        private static String toMessage(String description) {
            return "A failure occurred while executing " + description;
        }
    }

    private static class WorkItemExecution extends AbstractConditionalExecution<DefaultWorkResult> implements AsyncWorkCompletion {
        private final String description;

        public WorkItemExecution(String description, WorkerLease parentWorkerLease, Callable<DefaultWorkResult> callable) {
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
            child = parentWorkerLease.createChild();
            if (child.tryLock()) {
                return true;
            } else {
                child = null;
                return false;
            }
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
                throw new IllegalStateException("Detected attempt to access LazyChildWorkerLeaseLock before tryLock() has succeeded.  tryLock must be succeed before other methods are called.");
            }
            return child;
        }
    }
}
