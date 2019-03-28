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

import org.gradle.api.internal.classloading.GroovySystemLoader;
import org.gradle.api.internal.classloading.GroovySystemLoaderFactory;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.initialization.GradleApiUtil;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.classpath.DefaultClassPath;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.instantiation.DefaultInstantiatorFactory;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.serialize.ExceptionReplacingObjectInputStream;
import org.gradle.internal.serialize.ExceptionReplacingObjectOutputStream;
import org.gradle.util.GUtil;
import org.gradle.workers.IsolationMode;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.concurrent.Callable;

public class IsolatedClassloaderWorkerFactory implements WorkerFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final GroovySystemLoaderFactory groovySystemLoaderFactory = new GroovySystemLoaderFactory();

    public IsolatedClassloaderWorkerFactory(BuildOperationExecutor buildOperationExecutor) {
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public Worker getWorker(final DaemonForkOptions forkOptions) {
        return new AbstractWorker(buildOperationExecutor) {
            @Override
            public DefaultWorkResult execute(ActionExecutionSpec spec, BuildOperationRef parentBuildOperation) {
                return executeWrappedInBuildOperation(spec, parentBuildOperation, new Work() {
                    @Override
                    public DefaultWorkResult execute(ActionExecutionSpec spec) {
                        return executeInWorkerClassLoader(spec, forkOptions);
                    }
                });
            }
        };
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.CLASSLOADER;
    }

    private DefaultWorkResult executeInWorkerClassLoader(ActionExecutionSpec spec, DaemonForkOptions forkOptions) {
        ClassLoader workerInfrastructureClassloader = spec.getClass().getClassLoader();

        ClassLoaderStructure classLoaderStructure;
        if (forkOptions.getClassLoaderStructure() != null) {
            // This is used by groovy compilers
            classLoaderStructure = forkOptions.getClassLoaderStructure();
        } else {
            // Everything else uses the default
            classLoaderStructure = getDefaultClassLoaderStructure(workerInfrastructureClassloader, forkOptions.getClasspath());
        }

        ClassLoader workerClassLoader = createWorkerClassLoaderWithStructure(workerInfrastructureClassloader, classLoaderStructure);

        GroovySystemLoader workerClasspathGroovy = groovySystemLoaderFactory.forClassLoader(workerClassLoader);

        ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(workerClassLoader);
            Callable<?> worker = transferWorkerIntoWorkerClassloader(spec, workerClassLoader);
            Object result = worker.call();
            return transferResultFromWorkerClassLoader(result);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            workerClasspathGroovy.shutdown();
            CompositeStoppable.stoppable(workerClassLoader).stop();
            Thread.currentThread().setContextClassLoader(previousContextLoader);
        }
    }

    private ClassLoaderStructure getDefaultClassLoaderStructure(ClassLoader workerInfrastructureClassloader, Iterable<File> userClasspath) {
        FilteringClassLoader.Spec gradleApiFilter = GradleApiUtil.apiSpecFor(workerInfrastructureClassloader, DirectInstantiator.INSTANCE);
        VisitableURLClassLoader.Spec userSpec = new VisitableURLClassLoader.Spec("worker-loader", DefaultClassPath.of(userClasspath).getAsURLs());

        // Add the Gradle API filter between the user classloader and the worker infrastructure classloader
        return new ClassLoaderStructure(gradleApiFilter).withChild(userSpec);
    }

    private ClassLoader createWorkerClassLoaderWithStructure(ClassLoader workerInfrastructureClassloader, ClassLoaderStructure classLoaderStructure) {
        if (classLoaderStructure.getParent() == null) {
            // This is the highest parent in the hierarchy
            return createClassLoaderFromSpec(workerInfrastructureClassloader, classLoaderStructure.getSpec());
        } else {
            // Climb up the hierarchy looking for the highest parent
            ClassLoader parent = createWorkerClassLoaderWithStructure(workerInfrastructureClassloader, classLoaderStructure.getParent());
            return createClassLoaderFromSpec(parent, classLoaderStructure.getSpec());
        }
    }

    private ClassLoader createClassLoaderFromSpec(ClassLoader parent, ClassLoaderSpec spec) {
        if (spec instanceof VisitableURLClassLoader.Spec) {
            VisitableURLClassLoader.Spec visitableSpec = (VisitableURLClassLoader.Spec)spec;
            return new VisitableURLClassLoader(visitableSpec.getName(), parent, visitableSpec.getClasspath());
        } else if (spec instanceof FilteringClassLoader.Spec) {
            FilteringClassLoader.Spec filteringSpec = (FilteringClassLoader.Spec)spec;
            return new FilteringClassLoader(parent, filteringSpec);
        } else {
            throw new IllegalArgumentException("Can't handle spec of type " + spec.getClass().getName());
        }
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
            DefaultInstantiatorFactory instantiatorFactory = new DefaultInstantiatorFactory(new DefaultCrossBuildInMemoryCacheFactory(new DefaultListenerManager()), Collections.<InjectAnnotationHandler>emptyList());
            WorkerProtocol worker = new DefaultWorkerServer(instantiatorFactory.inject());
            return worker.execute(spec);
        }
    }
}
