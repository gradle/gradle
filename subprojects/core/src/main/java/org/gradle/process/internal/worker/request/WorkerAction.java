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

package org.gradle.process.internal.worker.request;

import org.gradle.api.Action;
import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.DefaultFileCollectionFactory;
import org.gradle.api.internal.file.DefaultFileLookup;
import org.gradle.api.internal.file.DefaultFilePropertyFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileFactory;
import org.gradle.api.internal.file.FileLookup;
import org.gradle.api.internal.file.FilePropertyFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.file.collections.DefaultDirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.ManagedFactories;
import org.gradle.api.internal.model.DefaultObjectFactory;
import org.gradle.api.internal.model.NamedObjectInstantiator;
import org.gradle.api.internal.provider.DefaultPropertyFactory;
import org.gradle.api.internal.provider.PropertyFactory;
import org.gradle.api.internal.provider.PropertyHost;
import org.gradle.api.internal.tasks.DefaultTaskDependencyFactory;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.properties.annotations.OutputPropertyRoleAnnotationHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.internal.DefaultProblems;
import org.gradle.api.problems.internal.ExceptionProblemRegistry;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.PatternSets;
import org.gradle.api.tasks.util.internal.PatternSpecFactory;
import org.gradle.cache.internal.CrossBuildInMemoryCacheFactory;
import org.gradle.cache.internal.DefaultCrossBuildInMemoryCacheFactory;
import org.gradle.internal.Cast;
import org.gradle.internal.Factory;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.concurrent.Stoppable;
import org.gradle.internal.dispatch.StreamCompletion;
import org.gradle.internal.event.DefaultListenerManager;
import org.gradle.internal.event.ListenerManager;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.generator.DefaultInstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.operations.CurrentBuildOperationRef;
import org.gradle.internal.remote.ObjectConnection;
import org.gradle.internal.remote.internal.hub.StreamFailureHandler;
import org.gradle.internal.service.Provides;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.ServiceRegistrationProvider;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.Scope.Global;
import org.gradle.internal.snapshot.impl.DefaultIsolatableFactory;
import org.gradle.internal.state.DefaultManagedFactoryRegistry;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.process.internal.worker.RequestHandler;
import org.gradle.process.internal.worker.WorkerProcessContext;
import org.gradle.process.internal.worker.child.WorkerLogEventListener;
import org.gradle.process.internal.worker.problem.WorkerProblemEmitter;
import org.gradle.tooling.internal.provider.serialization.ClassLoaderCache;
import org.gradle.tooling.internal.provider.serialization.DefaultPayloadClassLoaderRegistry;
import org.gradle.tooling.internal.provider.serialization.ModelClassLoaderFactory;
import org.gradle.tooling.internal.provider.serialization.PayloadSerializer;
import org.gradle.tooling.internal.provider.serialization.WellKnownClassLoaderRegistry;

import java.io.Serializable;
import java.util.concurrent.CountDownLatch;

import static java.util.Collections.emptyList;

/**
 * Worker-side implementation of {@link RequestProtocol} executing actions.
 */
@SuppressWarnings("unused")
public class WorkerAction implements Action<WorkerProcessContext>, Serializable, RequestProtocol, StreamFailureHandler, Stoppable, StreamCompletion {
    private final String workerImplementationName;
    private transient CountDownLatch completed;
    private transient ResponseProtocol responder;
    private transient WorkerLogEventListener workerLogEventListener;
    private transient RequestHandler<Object, Object> implementation;
    private transient Exception failure;

    public WorkerAction(Class<?> workerImplementation) {
        this.workerImplementationName = workerImplementation.getName();
    }

    public static class ProblemsServiceProvider implements ServiceRegistrationProvider {

        //        private final InstantiatorFactory instantiatorFactory;
        private final ResponseProtocol responder;

        public ProblemsServiceProvider(ResponseProtocol responder, InstantiatorFactory instantiatorFactory) {
            this.responder = responder;
//            this.instantiatorFactory = instantiatorFactory;
        }

        void configure(ServiceRegistration serviceRegistration) {
            serviceRegistration.add(FileLookup.class, DefaultFileLookup.class);
            serviceRegistration.add(FilePropertyFactory.class, FileFactory.class, DefaultFilePropertyFactory.class);
        }

        @Provides
        InstantiatorFactory createInstantiatorFactory() {
            return new DefaultInstantiatorFactory(
                new DefaultCrossBuildInMemoryCacheFactory(
                    new DefaultListenerManager(Global.class)), emptyList(),
                new OutputPropertyRoleAnnotationHandler(emptyList()));
        }

        @Provides
        ObjectFactory createObjectFactory(
            InstantiatorFactory instantiatorFactory,
            ServiceRegistry services,
            Factory<PatternSet> patternSetFactory,
            DirectoryFileTreeFactory directoryFileTreeFactory,
            PropertyFactory propertyFactory,
            FilePropertyFactory filePropertyFactory,
            TaskDependencyFactory taskDependencyFactory,
            FileCollectionFactory fileCollectionFactory,
            DomainObjectCollectionFactory domainObjectCollectionFactory,
            NamedObjectInstantiator namedObjectInstantiator
        ) {
            return new DefaultObjectFactory(
                instantiatorFactory.decorate(services),
                namedObjectInstantiator,
                directoryFileTreeFactory,
                patternSetFactory,
                propertyFactory,
                filePropertyFactory,
                taskDependencyFactory,
                fileCollectionFactory,
                domainObjectCollectionFactory);
        }

        @Provides
        PayloadSerializer createPayloadSerializer() {
            ClassLoaderCache classLoaderCache = new ClassLoaderCache();
            return new PayloadSerializer(
                new WellKnownClassLoaderRegistry(
                    new DefaultPayloadClassLoaderRegistry(
                        classLoaderCache,
                        new ModelClassLoaderFactory())));
        }

        @Provides
        PatternSpecFactory createPatternSpecFactory(ListenerManager listenerManager) {
            PatternSpecFactory patternSpecFactory = PatternSpecFactory.INSTANCE;
            listenerManager.addListener(patternSpecFactory);
            return patternSpecFactory;
        }

        @Provides
        DirectoryFileTreeFactory createDirectoryFileTreeFactory(Factory<PatternSet> patternSetFactory, FileSystem fileSystem) {
            return new DefaultDirectoryFileTreeFactory(patternSetFactory, fileSystem);
        }

        @Provides
        Factory<PatternSet> createPatternSetFactory(final PatternSpecFactory patternSpecFactory) {
            return PatternSets.getPatternSetFactory(patternSpecFactory);
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
        FileCollectionFactory createFileCollectionFactory(PathToFileResolver fileResolver, Factory<PatternSet> patternSetFactory, DirectoryFileTreeFactory directoryFileTreeFactory, PropertyHost propertyHost, FileSystem fileSystem) {
            return new DefaultFileCollectionFactory(fileResolver, DefaultTaskDependencyFactory.withNoAssociatedProject(), directoryFileTreeFactory, patternSetFactory, propertyHost, fileSystem);
        }

        @Provides
        FileResolver createFileResolver(FileLookup lookup) {
            return lookup.getFileResolver();
        }

        @Provides
        PropertyFactory createPropertyFactory(PropertyHost propertyHost) {
            return new DefaultPropertyFactory(propertyHost);
        }

        @Provides
        PropertyHost createPropertyHost() {
            return PropertyHost.NO_OP;
        }

//        @Provides
//        protected FileCollectionFactory createFileCollectionFactory(FileCollectionFactory parent, PathToFileResolver fileResolver, TaskDependencyFactory taskDependencyFactory, PropertyHost propertyHost) {
//            return parent.forChildScope(fileResolver, taskDependencyFactory, propertyHost);
//        }

        @Provides
        CrossBuildInMemoryCacheFactory createCrossBuildInMemoryCacheFactory(ListenerManager listenerManager) {
            return new DefaultCrossBuildInMemoryCacheFactory(listenerManager);
        }

        @Provides
        NamedObjectInstantiator createNamedObjectInstantiator(CrossBuildInMemoryCacheFactory cacheFactory) {
            return new NamedObjectInstantiator(cacheFactory);
        }

        @Provides
        ManagedFactoryRegistry createManagedFactoryRegistry(NamedObjectInstantiator namedObjectInstantiator, InstantiatorFactory instantiatorFactory, PropertyFactory propertyFactory, FileCollectionFactory fileCollectionFactory, FileFactory fileFactory, FilePropertyFactory filePropertyFactory) {
            return new DefaultManagedFactoryRegistry().withFactories(
                instantiatorFactory.getManagedFactory(),
                new ManagedFactories.ConfigurableFileCollectionManagedFactory(fileCollectionFactory),
                new org.gradle.api.internal.file.ManagedFactories.RegularFileManagedFactory(fileFactory),
                new org.gradle.api.internal.file.ManagedFactories.RegularFilePropertyManagedFactory(filePropertyFactory),
                new org.gradle.api.internal.file.ManagedFactories.DirectoryManagedFactory(fileFactory),
                new org.gradle.api.internal.file.ManagedFactories.DirectoryPropertyManagedFactory(filePropertyFactory),
                new org.gradle.api.internal.provider.ManagedFactories.SetPropertyManagedFactory(propertyFactory),
                new org.gradle.api.internal.provider.ManagedFactories.ListPropertyManagedFactory(propertyFactory),
                new org.gradle.api.internal.provider.ManagedFactories.MapPropertyManagedFactory(propertyFactory),
                new org.gradle.api.internal.provider.ManagedFactories.PropertyManagedFactory(propertyFactory),
                new org.gradle.api.internal.provider.ManagedFactories.ProviderManagedFactory(),
                namedObjectInstantiator
            );
        }

        @Provides
        InternalProblems createInternalProblems(PayloadSerializer payloadSerializer, ServiceRegistry serviceRegistry, IsolatableFactory isolatableFactory, IsolatableSerializerRegistry isolatableSerializerRegistry, InstantiatorFactory instantiatorFactory) {
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

        @Provides
        TaskDependencyFactory createTaskDependencyFactory() {
            return DefaultTaskDependencyFactory.withNoAssociatedProject();
        }

        @Provides
        DomainObjectCollectionFactory createDomainObjectCollectionFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry services) {
            return new DefaultDomainObjectCollectionFactory(instantiatorFactory, services, CollectionCallbackActionDecorator.NOOP, MutationGuards.identity());
        }
    }

    @Override
    public void execute(WorkerProcessContext workerProcessContext) {
        completed = new CountDownLatch(1);

        ObjectConnection connection = workerProcessContext.getServerConnection();
        connection.addIncoming(RequestProtocol.class, this);
        responder = connection.addOutgoing(ResponseProtocol.class);
        ServiceRegistry parentServices = workerProcessContext.getServiceRegistry();
//        DefaultProblems internalProblems = (DefaultProblems) parentServices.get(InternalProblems.class);
        DefaultProblems.problemSummarizer = new WorkerProblemEmitter(responder);

        workerLogEventListener = parentServices.get(WorkerLogEventListener.class);
        RequestArgumentSerializers argumentSerializers = new RequestArgumentSerializers();
        try {
//            DefaultInstantiatorFactory instantiatorFactory = new DefaultInstantiatorFactory(
//                    new DefaultCrossBuildInMemoryCacheFactory(
//                        new DefaultListenerManager(Global.class)), Collections.emptyList(),
//                    new OutputPropertyRoleAnnotationHandler(Collections.emptyList()));
            ServiceRegistry serviceRegistry = ServiceRegistryBuilder.builder()
                .displayName("worker action services")
                .parent(parentServices)
//                .provider(new WorkerSharedGlobalScopeServices(ClassPath.EMPTY))
//                .provider(new WorkerSharedProjectScopeServices(null))
                .provider(registration -> {
                    // Make the argument serializers available so work implementations can register their own serializers
                    registration.add(RequestArgumentSerializers.class, argumentSerializers);
//                    registration.add(InstantiatorFactory.class, instantiatorFactory);
                    // TODO we should inject a worker-api specific implementation of InternalProblems here
                    registration.addProvider(new ProblemsServiceProvider(responder, null));
                })
                .build();

            Class<?> workerImplementation = Class.forName(workerImplementationName);
            implementation = Cast.uncheckedNonnullCast(serviceRegistry.get(InstantiatorFactory.class).inject(serviceRegistry).newInstance(workerImplementation));
        } catch (Exception e) {
            failure = e;
        }

        if (failure == null) {
            connection.useParameterSerializers(RequestSerializerRegistry.create(this.getClass().getClassLoader(), argumentSerializers));
        } else {
            // Discard incoming requests, as the serializers may not have been configured
            connection.useParameterSerializers(RequestSerializerRegistry.createDiscardRequestArg());
            // Notify the client
            responder.infrastructureFailed(failure);
        }

        connection.connect();

        try {
            completed.await();
        } catch (InterruptedException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void stop() {
        completed.countDown();
        CurrentBuildOperationRef.instance().clear();
    }

    @Override
    public void endStream() {
        // This happens when the connection between the worker and the build daemon is closed for some reason,
        // possibly because the build daemon died unexpectedly.
        stop();
    }

    @Override
    public void runThenStop(Request request) {
        try {
            run(request);
        } finally {
            stop();
        }
    }

    @Override
    public void run(Request request) {
        if (failure != null) {
            // Ignore
            return;
        }
        CurrentBuildOperationRef.instance().with(request.getBuildOperation(), () -> {
            try {
                Object result;
                try {
                    // We want to use the responder as the logging protocol object here because log messages from the
                    // action will have the build operation associated.  By using the responder, we ensure that all
                    // messages arrive on the same incoming queue in the build process and the completed message will only
                    // arrive after all log messages have been processed.
                    result = workerLogEventListener.withWorkerLoggingProtocol(responder, () -> implementation.run(request.getArg()));
                } catch (Throwable failure) {
                    if (failure instanceof NoClassDefFoundError) {
                        // Assume an infrastructure problem
                        responder.infrastructureFailed(failure);
                    } else {
                        responder.failed(failure);
                    }
                    return;
                }
                responder.completed(result);
            } catch (Throwable t) {
                responder.infrastructureFailed(t);
            }
        });
    }

    @Override
    public void handleStreamFailure(Throwable t) {
        responder.failed(t);
    }
}
