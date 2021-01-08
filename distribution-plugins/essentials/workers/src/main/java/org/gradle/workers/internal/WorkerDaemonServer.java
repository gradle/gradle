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

import org.gradle.api.internal.CollectionCallbackActionDecorator;
import org.gradle.api.internal.MutationGuards;
import org.gradle.api.internal.collections.DefaultDomainObjectCollectionFactory;
import org.gradle.api.internal.collections.DomainObjectCollectionFactory;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.internal.provider.DefaultProviderFactory;
import org.gradle.api.internal.resources.DefaultResourceHandler;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.resources.ReadableResource;
import org.gradle.api.resources.ResourceHandler;
import org.gradle.api.resources.TextResourceFactory;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.internal.hash.ClassLoaderHierarchyHasher;
import org.gradle.internal.hash.FileHasher;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolation.IsolatableFactory;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.WorkerSharedGlobalScopeServices;
import org.gradle.internal.service.scopes.WorkerSharedProjectScopeServices;
import org.gradle.internal.service.scopes.WorkerSharedUserHomeScopeServices;
import org.gradle.internal.state.ManagedFactoryRegistry;
import org.gradle.process.internal.ExecFactory;
import org.gradle.process.internal.worker.RequestHandler;
import org.gradle.process.internal.worker.request.RequestArgumentSerializers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import java.io.File;

public class WorkerDaemonServer implements RequestHandler<TransportableActionExecutionSpec, DefaultWorkResult> {
    private final ServiceRegistry internalServices;
    private final LegacyTypesSupport legacyTypesSupport;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private final InstantiatorFactory instantiatorFactory;
    private ClassLoader workerClassLoader;

    @Inject
    public WorkerDaemonServer(ServiceRegistry parentServices, RequestArgumentSerializers argumentSerializers) {
        this.internalServices = createWorkerDaemonServices(parentServices);
        this.legacyTypesSupport = internalServices.get(LegacyTypesSupport.class);
        this.actionExecutionSpecFactory = internalServices.get(ActionExecutionSpecFactory.class);
        this.instantiatorFactory = internalServices.get(InstantiatorFactory.class);
        argumentSerializers.register(TransportableActionExecutionSpec.class, new TransportableActionExecutionSpecSerializer());
    }

    static ServiceRegistry createWorkerDaemonServices(ServiceRegistry parent) {
        return ServiceRegistryBuilder.builder()
            .displayName("worker daemon services")
            .parent(parent)
            .provider(new WorkerSharedGlobalScopeServices())
            .provider(new WorkerDaemonServices())
            .build();
    }

    @Override
    public DefaultWorkResult run(TransportableActionExecutionSpec spec) {
        try {
            try (WorkerProjectServices internalServices = new WorkerProjectServices(spec.getBaseDir(), this.internalServices)) {
                RequestHandler<TransportableActionExecutionSpec, DefaultWorkResult> worker = getIsolatedClassloaderWorker(spec.getClassLoaderStructure(), internalServices);
                return worker.run(spec);
            }
        } catch (Throwable t) {
            return new DefaultWorkResult(true, t);
        }
    }

    private RequestHandler<TransportableActionExecutionSpec, DefaultWorkResult> getIsolatedClassloaderWorker(ClassLoaderStructure classLoaderStructure, ServiceRegistry workServices) {
        if (classLoaderStructure instanceof FlatClassLoaderStructure) {
            return new FlatClassLoaderWorker(this.getClass().getClassLoader(), workServices, actionExecutionSpecFactory, instantiatorFactory);
        } else {
            return new IsolatedClassloaderWorker(getWorkerClassLoader(classLoaderStructure), workServices, actionExecutionSpecFactory, instantiatorFactory, true);
        }
    }

    private ClassLoader getWorkerClassLoader(ClassLoaderStructure classLoaderStructure) {
        if (workerClassLoader == null) {
            this.workerClassLoader = IsolatedClassloaderWorker.createIsolatedWorkerClassloader(classLoaderStructure, this.getClass().getClassLoader(), legacyTypesSupport);
        }
        return workerClassLoader;
    }

    @Override
    public String toString() {
        return "WorkerDaemonServer{}";
    }

    private static class WorkerDaemonServices extends WorkerSharedUserHomeScopeServices {

        // TODO: configuration-cache - deprecate workers access to ProviderFactory?
        ProviderFactory createProviderFactory() {
            return new DefaultProviderFactory();
        }

        IsolatableSerializerRegistry createIsolatableSerializerRegistry(ClassLoaderHierarchyHasher classLoaderHierarchyHasher, ManagedFactoryRegistry managedFactoryRegistry) {
            return new IsolatableSerializerRegistry(classLoaderHierarchyHasher, managedFactoryRegistry);
        }

        ActionExecutionSpecFactory createActionExecutionSpecFactory(IsolatableFactory isolatableFactory, IsolatableSerializerRegistry serializerRegistry) {
            return new DefaultActionExecutionSpecFactory(isolatableFactory, serializerRegistry);
        }

        ClassLoaderHierarchyHasher createClassLoaderHierarchyHasher() {
            // Return a dummy implementation of this as creating a real hasher drags ~20 more services
            // along with it, and a hasher isn't actually needed on the worker process side at the moment.
            return new ClassLoaderHierarchyHasher() {
                @Nullable
                @Override
                public HashCode getClassLoaderHash(@Nonnull ClassLoader classLoader) {
                    throw new UnsupportedOperationException();
                }
            };
        }

        DomainObjectCollectionFactory createDomainObjectCollectionFactory(InstantiatorFactory instantiatorFactory, ServiceRegistry services) {
            return new DefaultDomainObjectCollectionFactory(instantiatorFactory, services, CollectionCallbackActionDecorator.NOOP, MutationGuards.identity());
        }
    }

    static class WorkerProjectServices extends DefaultServiceRegistry {
        public WorkerProjectServices(File baseDir, ServiceRegistry... parents) {
            super("worker file services for " + baseDir.getAbsolutePath(), parents);
            addProvider(new WorkerSharedProjectScopeServices(baseDir));
        }

        protected Instantiator createInstantiator(InstantiatorFactory instantiatorFactory) {
            return instantiatorFactory.decorateLenient(this);
        }

        protected ExecFactory createExecFactory(ExecFactory execFactory, FileResolver fileResolver, FileCollectionFactory fileCollectionFactory, Instantiator instantiator, ObjectFactory objectFactory) {
            return execFactory.forContext(fileResolver, fileCollectionFactory, instantiator, objectFactory);
        }

        protected DefaultResourceHandler.Factory createResourceHandlerFactory() {
            // We use a dummy implementation of this as creating a real resource handler would require us to add
            // an additional jar to the worker runtime startup and a resource handler isn't actually needed in
            // the worker process.
            ResourceHandler resourceHandler = new ResourceHandler() {
                @Override
                public ReadableResource gzip(Object path) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public ReadableResource bzip2(Object path) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public TextResourceFactory getText() {
                    throw new UnsupportedOperationException();
                }
            };

            return fileOperations -> resourceHandler;
        }

        FileHasher createFileHasher() {
            // Return a dummy implementation of this as creating a real file hasher drags numerous other services
            // along with it, and a file hasher isn't actually needed on the worker process side at the moment.
            return new FileHasher() {
                @Override
                public HashCode hash(File file) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public HashCode hash(File file, long length, long lastModified) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
