/*
 * Copyright 2019 the original author or authors.
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
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.io.ClassLoaderObjectInputStream;
import org.gradle.internal.serialize.ExceptionReplacingObjectInputStream;
import org.gradle.internal.serialize.ExceptionReplacingObjectOutputStream;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.GUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.concurrent.Callable;

public class IsolatedClassloaderWorker implements Worker {
    private final GroovySystemLoaderFactory groovySystemLoaderFactory = new GroovySystemLoaderFactory();
    private final ClassLoaderStructure classLoaderStructure;
    private final ClassLoader workerInfrastructureClassloader;
    private final ServiceRegistry serviceRegistry;
    private ClassLoader workerClassLoader;
    private boolean reuseClassloader;

    public IsolatedClassloaderWorker(ClassLoaderStructure classLoaderStructure, ClassLoader workerInfrastructureClassloader, ServiceRegistry serviceRegistry) {
        this.classLoaderStructure = classLoaderStructure;
        this.workerInfrastructureClassloader = workerInfrastructureClassloader;
        this.serviceRegistry = serviceRegistry;
    }

    public IsolatedClassloaderWorker(ClassLoaderStructure classLoaderStructure, ClassLoader workerInfrastructureClassloader, ServiceRegistry serviceRegistry, boolean reuseClassloader) {
        this(classLoaderStructure, workerInfrastructureClassloader, serviceRegistry);
        this.reuseClassloader = reuseClassloader;
    }

    public DefaultWorkResult execute(ActionExecutionSpec spec) {
        ClassLoader workerClassLoader = getWorkerClassLoader();
        GroovySystemLoader workerClasspathGroovy = groovySystemLoaderFactory.forClassLoader(workerClassLoader);

        ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(workerClassLoader);
            Callable<?> worker = transferWorkerIntoWorkerClassloader(spec, workerClassLoader);
            injectServiceRegistry(worker);
            Object result = worker.call();
            return transferResultFromWorkerClassLoader(result);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            workerClasspathGroovy.shutdown();
            // TODO: we should just cache these classloaders and eject/stop them when they are no longer in use
            if (!reuseClassloader) {
                CompositeStoppable.stoppable(workerClassLoader).stop();
                this.workerClassLoader = null;
            }
            Thread.currentThread().setContextClassLoader(previousContextLoader);
        }
    }

    private void injectServiceRegistry(Object worker) {
        try {
            Method setServicesMethod = worker.getClass().getDeclaredMethod("setServiceRegistry", ServiceRegistry.class);
            setServicesMethod.invoke(worker, serviceRegistry);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private ClassLoader getWorkerClassLoader() {
        if (workerClassLoader == null) {
            workerClassLoader = createWorkerClassloader();
        }
        return workerClassLoader;
    }

    private ClassLoader createWorkerClassloader() {
        if (classLoaderStructure == null) {
            throw new IllegalStateException("ClassLoaderStructure cannot be null");
        }

        return createWorkerClassLoaderWithStructure(workerInfrastructureClassloader, classLoaderStructure);
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
        byte[] serializedWorker;
        try {
            serializedWorker = GUtil.serialize(new WorkerCallable(spec));
        } catch (Exception e) {
            throw new WorkSerializationException("Could not serialize unit of work", e);
        }

        try {
            ObjectInputStream ois = new ClassLoaderObjectInputStream(new ByteArrayInputStream(serializedWorker), workerClassLoader);
            return (Callable<?>) ois.readObject();
        } catch (Exception e) {
            throw new WorkSerializationException("Could not deserialize unit of work", e);
        }
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
    public static class WorkerCallable implements Callable<Object>, Serializable {
        private final ActionExecutionSpec spec;
        private ServiceRegistry serviceRegistry;

        private WorkerCallable(ActionExecutionSpec spec) {
            this.spec = spec;
        }

        @Override
        public Object call() throws Exception {
            ActionExecutionSpec effectiveSpec = spec;
            if (spec instanceof WrappedActionExecutionSpec) {
                effectiveSpec = ((WrappedActionExecutionSpec) spec).unwrap(Thread.currentThread().getContextClassLoader());
            }
            WorkerProtocol worker = new DefaultWorkerServer(serviceRegistry);
            return worker.execute(effectiveSpec);
        }

        public void setServiceRegistry(ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry;
        }
    }
}
