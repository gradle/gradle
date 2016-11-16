/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal.daemon;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.daemon.WorkerDaemonExecutionException;
import org.gradle.process.daemon.WorkerDaemonExecutor;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.util.CollectionUtils;
import org.gradle.util.GUtil;

import java.io.File;
import java.io.Serializable;
import java.util.Set;

public abstract class AbstractWorkerDaemonExecutor<T> implements WorkerDaemonExecutor {
    private final WorkerDaemonFactory workerDaemonFactory;
    private final JavaForkOptions javaForkOptions;
    private final Set<File> classpath = Sets.newLinkedHashSet();
    private final Class<? extends T> implementationClass;
    private final Class<? extends WorkerDaemonProtocol> serverImplementationClass;
    private Serializable[] params = new Serializable[]{};

    public AbstractWorkerDaemonExecutor(WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver, Class<? extends T> implementationClass, Class<? extends WorkerDaemonProtocol> serverImplementationClass) {
        this.workerDaemonFactory = workerDaemonFactory;
        this.javaForkOptions = new DefaultJavaForkOptions(fileResolver);
        this.implementationClass = implementationClass;
        this.serverImplementationClass = serverImplementationClass;
        this.javaForkOptions.workingDir(new File("").getAbsoluteFile());
    }

    @Override
    public WorkerDaemonExecutor classpath(Iterable<File> files) {
        GUtil.addToCollection(classpath, files);
        return this;
    }

    @Override
    public WorkerDaemonExecutor forkOptions(Action<? super JavaForkOptions> forkOptionsAction) {
        forkOptionsAction.execute(javaForkOptions);
        return this;
    }

    @Override
    public JavaForkOptions getForkOptions() {
        return javaForkOptions;
    }

    @Override
    public WorkerDaemonExecutor params(Serializable... params) {
        this.params = params;
        return this;
    }

    protected Class<? extends T> getImplementationClass() {
        return implementationClass;
    }

    protected Serializable[] getParams() {
        return params;
    }

    abstract WorkSpec getSpec();

    abstract WorkerDaemonAction getAction();

    @Override
    public void execute() {
        final WorkSpec spec = getSpec();
        final WorkerDaemonAction action = getAction();
        try {
            WorkerDaemon daemon = workerDaemonFactory.getDaemon(serverImplementationClass, getForkOptions().getWorkingDir(), getDaemonForkOptions());
            WorkerDaemonResult result = daemon.execute(action, spec);
            if (!result.isSuccess()) {
                throw result.getException();
            }
        } catch (Throwable t) {
            throw new WorkerDaemonExecutionException("A failure occurred while executing " + action.getDescription(), t);
        }
    }

    DaemonForkOptions getDaemonForkOptions() {
        Iterable<Class<?>> paramTypes = CollectionUtils.collect(getParams(), new Transformer<Class<?>, Object>() {
            @Override
            public Class<?> transform(Object o) {
                return o.getClass();
            }
        });
        return toDaemonOptions(implementationClass, paramTypes, javaForkOptions, classpath);
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
}
