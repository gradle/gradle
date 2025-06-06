/*
 * Copyright 2025 the original author or authors.
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

import org.gradle.api.internal.file.DefaultFileLookup;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.initialization.loadercache.ModelClassLoaderFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.api.problems.internal.ExceptionProblemRegistry;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory;
import org.gradle.internal.snapshot.impl.IsolatableSerializerRegistry;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.process.internal.worker.problem.WorkerProblemEmitter;
import org.gradle.process.internal.worker.problem.WorkerProblemProtocol;
import org.gradle.process.internal.worker.request.WorkerAction;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;
import org.jspecify.annotations.NonNull;

public class WorkerProcessIsolationProblemsServiceProvider implements ServiceRegistrationProvider {

    void configure(ServiceRegistration serviceRegistration) {
        serviceRegistration.add(FileLookup.class, DefaultFileLookup.class);
    }

    @NonNull
    @Provides
    PayloadSerializer createPayloadSerializer() {
        ClassLoaderCache classLoaderCache = new ClassLoaderCache();

        ClassLoader parent = WorkerAction.class.getClassLoader();
        FilteringClassLoader.Spec filterSpec = new FilteringClassLoader.Spec();
        FilteringClassLoader modelClassLoader = new FilteringClassLoader(parent, filterSpec);

        return new PayloadSerializer(
            new WellKnownClassLoaderRegistry(
                new DefaultPayloadClassLoaderRegistry(
                    classLoaderCache,
                    new ModelClassLoaderFactory(modelClassLoader))));
    }

    @Provides
    PatternSpecFactory createPatternSpecFactory(ListenerManager listenerManager) {
        PatternSpecFactory patternSpecFactory = PatternSpecFactory.INSTANCE;
        listenerManager.addListener(patternSpecFactory);
        return patternSpecFactory;
    }

    @Provides
    ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher() {
        return classLoader -> {
            throw new UnsupportedOperationException();
        };
    }

    @Provides
    IsolatableSerializerRegistry createIsolatableSerializerRegistry(
        ManagedFactoryRegistry managedFactoryRegistry,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher
    ) {
        return new IsolatableSerializerRegistry(classLoaderHierarchyHasher, managedFactoryRegistry);
    }

    @Provides
    IsolatableFactory createIsolatableFactory(
        ManagedFactoryRegistry managedFactoryRegistry,
        ClassLoaderHierarchyHasher classLoaderHierarchyHasher
    ) {
        return new DefaultIsolatableFactory(classLoaderHierarchyHasher, managedFactoryRegistry);
    }

    @Provides
    PropertyHost createPropertyHost() {
        return PropertyHost.NO_OP;
    }

    @Provides
    InternalProblems createInternalProblems(
        PayloadSerializer payloadSerializer,
        ServiceRegistry serviceRegistry,
        IsolatableFactory isolatableFactory,
        IsolatableSerializerRegistry isolatableSerializerRegistry,
        InstantiatorFactory instantiatorFactory,
        WorkerProblemProtocol responder
    ) {
        return new DefaultProblems(
            new WorkerProblemEmitter(responder),
            null,
            CurrentBuildOperationRef.instance(),
            new ExceptionProblemRegistry(),
            null,
            instantiatorFactory.decorate(serviceRegistry),
            payloadSerializer,
            isolatableFactory,
            isolatableSerializerRegistry
        );
    }
}
