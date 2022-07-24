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

import org.gradle.initialization.ClassLoaderRegistry;
import org.gradle.initialization.LegacyTypesSupport;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.process.internal.worker.RequestHandler;

public class IsolatedClassloaderWorkerFactory implements WorkerFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ServiceRegistry internalServices;
    private final ClassLoaderRegistry classLoaderRegistry;
    private final LegacyTypesSupport legacyTypesSupport;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;
    private final InstantiatorFactory instantiatorFactory;

    public IsolatedClassloaderWorkerFactory(BuildOperationExecutor buildOperationExecutor, ServiceRegistry internalServices, ClassLoaderRegistry classLoaderRegistry, LegacyTypesSupport legacyTypesSupport, ActionExecutionSpecFactory actionExecutionSpecFactory, InstantiatorFactory instantiatorFactory) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.internalServices = internalServices;
        this.classLoaderRegistry = classLoaderRegistry;
        this.legacyTypesSupport = legacyTypesSupport;
        this.actionExecutionSpecFactory = actionExecutionSpecFactory;
        this.instantiatorFactory = instantiatorFactory;
    }

    @Override
    public BuildOperationAwareWorker getWorker(WorkerRequirement workerRequirement) {
        return new AbstractWorker(buildOperationExecutor) {
            @Override
            public DefaultWorkResult execute(IsolatedParametersActionExecutionSpec<?> spec, BuildOperationRef parentBuildOperation) {
                return executeWrappedInBuildOperation(spec, parentBuildOperation, workSpec -> {
                    // Serialize the incoming class and parameters
                    TransportableActionExecutionSpec transportableSpec = actionExecutionSpecFactory.newTransportableSpec(spec);

                    ClassLoader workerInfrastructureClassloader = classLoaderRegistry.getPluginsClassLoader();
                    ClassLoaderStructure classLoaderStructure = ((IsolatedClassLoaderWorkerRequirement) workerRequirement).getClassLoaderStructure();
                    ClassLoader workerClassLoader = IsolatedClassloaderWorker.createIsolatedWorkerClassloader(classLoaderStructure, workerInfrastructureClassloader, legacyTypesSupport);
                    RequestHandler<TransportableActionExecutionSpec, DefaultWorkResult> worker = new IsolatedClassloaderWorker(workerClassLoader, internalServices, actionExecutionSpecFactory, instantiatorFactory);
                    return worker.run(transportableSpec);
                });
            }
        };
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.CLASSLOADER;
    }
}
