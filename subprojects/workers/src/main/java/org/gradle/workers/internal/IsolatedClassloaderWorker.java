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
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.initialization.MixInLegacyTypesClassLoader;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.classloader.ClassLoaderSpec;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.classloader.VisitableURLClassLoader;
import org.gradle.internal.concurrent.CompositeStoppable;
import org.gradle.internal.service.ServiceRegistry;

public class IsolatedClassloaderWorker extends AbstractClassLoaderWorker {
    private final GroovySystemLoaderFactory groovySystemLoaderFactory = new GroovySystemLoaderFactory();
    private final ClassLoaderStructure classLoaderStructure;
    private final ClassLoader workerInfrastructureClassloader;
    private final ServiceRegistry serviceRegistry;
    private ClassLoader workerClassLoader;
    private boolean reuseClassloader;

    public IsolatedClassloaderWorker(ClassLoaderStructure classLoaderStructure, ClassLoader workerInfrastructureClassloader, ServiceRegistry serviceRegistry) {
        super(serviceRegistry);
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

        try {
            return executeInClassLoader(spec, workerClassLoader);
        } catch (Exception e) {
            throw UncheckedException.throwAsUncheckedException(e);
        } finally {
            workerClasspathGroovy.shutdown();
            // TODO: we should just cache these classloaders and eject/stop them when they are no longer in use
            if (!reuseClassloader) {
                CompositeStoppable.stoppable(workerClassLoader).stop();
                this.workerClassLoader = null;
            }
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
        if (spec instanceof MixInLegacyTypesClassLoader.Spec) {
            MixInLegacyTypesClassLoader.Spec mixinSpec = (MixInLegacyTypesClassLoader.Spec) spec;
            if (mixinSpec.getClasspath().isEmpty()) {
                return parent;
            }
            return new MixInLegacyTypesClassLoader(parent, mixinSpec.getClasspath(), serviceRegistry.get(LegacyTypesSupport.class));
        } else if (spec instanceof VisitableURLClassLoader.Spec) {
            VisitableURLClassLoader.Spec visitableSpec = (VisitableURLClassLoader.Spec)spec;
            if (visitableSpec.getClasspath().isEmpty()) {
                return parent;
            }
            return new VisitableURLClassLoader(visitableSpec.getName(), parent, visitableSpec.getClasspath());
        } else if (spec instanceof FilteringClassLoader.Spec) {
            FilteringClassLoader.Spec filteringSpec = (FilteringClassLoader.Spec)spec;
            if (filteringSpec.isEmpty()) {
                return parent;
            }
            return new FilteringClassLoader(parent, filteringSpec);
        } else {
            throw new IllegalArgumentException("Can't handle spec of type " + spec.getClass().getName());
        }
    }
}
