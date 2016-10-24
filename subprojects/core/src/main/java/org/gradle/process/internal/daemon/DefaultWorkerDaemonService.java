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

import com.google.common.collect.ImmutableList;
import org.gradle.api.Action;
import org.gradle.api.Transformer;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.process.JavaForkOptions;
import org.gradle.process.daemon.DaemonForkOptions;
import org.gradle.process.daemon.WorkerDaemonService;
import org.gradle.process.internal.DefaultJavaForkOptions;
import org.gradle.util.CollectionUtils;

import java.io.File;

public class DefaultWorkerDaemonService implements WorkerDaemonService {
    private final WorkerDaemonFactory workerDaemonFactory;
    private final FileResolver fileResolver;

    public DefaultWorkerDaemonService(WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver) {
        this.workerDaemonFactory = workerDaemonFactory;
        this.fileResolver = fileResolver;
    }

    @Override
    public JavaForkOptions newForkOptions() {
        return new DefaultJavaForkOptions(fileResolver);
    }

    @Override
    public Runnable daemonRunnable(final JavaForkOptions forkOptions, final Iterable<File> classpath, final Iterable<String> sharedPackages, final Class<? extends Runnable> runnableClass, final Object... params) {
        final ParamSpec spec = new ParamSpec(params);
        final WrappedDaemonRunnable daemonRunnable = new WrappedDaemonRunnable(runnableClass);
        Iterable<Class<?>> paramTypes = CollectionUtils.collect(params, new Transformer<Class<?>, Object>() {
            @Override
            public Class<?> transform(Object o) {
                return o.getClass();
            }
        });
        final DaemonForkOptions daemonForkOptions = toDaemonOptions(runnableClass, paramTypes, forkOptions, classpath, sharedPackages);

        return new Runnable() {
            @Override
            public void run() {
                WorkerDaemon daemon = workerDaemonFactory.getDaemon(forkOptions.getWorkingDir(), daemonForkOptions);
                daemon.execute(daemonRunnable, spec);
            }
        };
    }

    private DaemonForkOptions toDaemonOptions(Class<?> actionClass, Iterable<Class<?>> paramClasses, JavaForkOptions forkOptions, Iterable<File> classpath, Iterable<String> sharedPackages) {
        ImmutableList.Builder<File> classpathBuilder = ImmutableList.builder();
        ImmutableList.Builder<String> sharedPackagesBuilder = ImmutableList.builder();

        if (classpath != null) {
            classpathBuilder.addAll(classpath);
        }

        classpathBuilder.add(ClasspathUtil.getClasspathForClass(Action.class));
        classpathBuilder.add(ClasspathUtil.getClasspathForClass(actionClass));

        if (sharedPackages != null) {
            sharedPackagesBuilder.addAll(sharedPackages);
        }

        if (actionClass.getPackage() != null) {
            sharedPackagesBuilder.add(actionClass.getPackage().getName());
        }

        sharedPackagesBuilder.add("org.gradle.api");

        for (Class<?> paramClass : paramClasses) {
            if (paramClass.getClassLoader() != null) {
                classpathBuilder.add(ClasspathUtil.getClasspathForClass(paramClass));
            }
            if (paramClass.getPackage() != null) {
                sharedPackagesBuilder.add(paramClass.getPackage().getName());
            }
        }

        Iterable<File> daemonClasspath = classpathBuilder.build();
        Iterable<String> daemonSharedPackages = sharedPackagesBuilder.build();

        return new DaemonForkOptions(forkOptions.getMinHeapSize(), forkOptions.getMaxHeapSize(), forkOptions.getAllJvmArgs(), daemonClasspath, daemonSharedPackages);
    }

    private static class ParamSpec implements WorkSpec {
        final Object[] params;

        public ParamSpec(Object[] params) {
            this.params = params;
        }

        public Object[] getParams() {
            return params;
        }
    }

    private static class WrappedDaemonRunnable implements WorkerDaemonAction<ParamSpec> {
        private final Class<? extends Runnable> runnableClass;

        public WrappedDaemonRunnable(Class<? extends Runnable> runnableClass) {
            this.runnableClass = runnableClass;
        }

        @Override
        public WorkerDaemonResult execute(ParamSpec spec) {
            try {
                Runnable runnable = DirectInstantiator.instantiate(runnableClass, spec.getParams());
                runnable.run();
                return new WorkerDaemonResult(true, null);
            } catch (Throwable t) {
                return new WorkerDaemonResult(true, t);
            }
        }
    }
}
