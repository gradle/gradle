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

import org.gradle.internal.service.ServiceRegistry;

public abstract class AbstractClassLoaderWorker implements Worker {
    private final WorkerProtocol worker;
    private final ActionExecutionSpecFactory actionExecutionSpecFactory;

    public AbstractClassLoaderWorker(ServiceRegistry serviceRegistry) {
        this.worker = new DefaultWorkerServer(serviceRegistry);
        this.actionExecutionSpecFactory = serviceRegistry.get(ActionExecutionSpecFactory.class);
    }

    public DefaultWorkResult executeInClassLoader(ActionExecutionSpec spec, ClassLoader workerClassLoader) {
        ClassLoader previousContextLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(workerClassLoader);
            // Serialize the incoming class and parameters (if necessary)
            TransportableActionExecutionSpec transportableSpec = actionExecutionSpecFactory.newTransportableSpec(spec);

            // Deserialize the class and parameters in the workerClassLoader (the context classloader)
            ActionExecutionSpec effectiveSpec = actionExecutionSpecFactory.newSimpleSpec(transportableSpec);

            return worker.execute(effectiveSpec);
        } finally {
            Thread.currentThread().setContextClassLoader(previousContextLoader);
        }
    }
}
