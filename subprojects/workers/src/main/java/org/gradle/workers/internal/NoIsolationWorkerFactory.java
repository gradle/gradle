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

import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.BuildOperationRef;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;

public class NoIsolationWorkerFactory implements WorkerFactory {
    private final BuildOperationExecutor buildOperationExecutor;
    private final ServiceRegistry parent;
    private DefaultServiceRegistry serviceRegistry;
    private WorkerExecutor workerExecutor;

    public NoIsolationWorkerFactory(BuildOperationExecutor buildOperationExecutor, ServiceRegistry parent) {
        this.buildOperationExecutor = buildOperationExecutor;
        this.parent = parent;
    }

    // Attaches the owning WorkerExecutor to this factory
    public void setWorkerExecutor(WorkerExecutor workerExecutor) {
        this.workerExecutor = workerExecutor;
        this.serviceRegistry = new DefaultServiceRegistry(parent);
        serviceRegistry.add(WorkerExecutor.class, workerExecutor);
    }

    @Override
    public BuildOperationAwareWorker getWorker(final DaemonForkOptions forkOptions) {
        final WorkerExecutor workerExecutor = this.workerExecutor;
        return new AbstractWorker(buildOperationExecutor) {
            @Override
            public DefaultWorkResult execute(ActionExecutionSpec spec, BuildOperationRef parentBuildOperation) {
                return executeWrappedInBuildOperation(spec, parentBuildOperation, new Work() {
                    @Override
                    public DefaultWorkResult execute(ActionExecutionSpec spec) {
                        DefaultWorkResult result;
                        try {
                            // TODO This should use the isolation framework to isolate the parameters instead of wrapping/unwrapping the parameters
                            ActionExecutionSpec effectiveSpec = ((SerializedParametersActionExecutionSpec)spec).deserialize(spec.getImplementationClass().getClassLoader());
                            WorkerProtocol workerServer = new DefaultWorkerServer(serviceRegistry);
                            result = workerServer.execute(effectiveSpec);
                        } finally {
                            //TODO the async work tracker should wait for children of an operation to finish first.
                            //It should not be necessary to call it here.
                            workerExecutor.await();
                        }
                        return result;
                    }
                });
            }
        };
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.NONE;
    }
}
