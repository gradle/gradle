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

import org.gradle.api.internal.InstantiatorFactory;
import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.internal.service.DefaultServiceRegistry;
import org.gradle.internal.work.AsyncWorkTracker;
import org.gradle.internal.work.WorkerLeaseRegistry;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;

public class NoIsolationWorkerFactory implements WorkerFactory {
    private final WorkerLeaseRegistry workerLeaseRegistry;
    private final BuildOperationExecutor buildOperationExecutor;
    private final AsyncWorkTracker workTracker;
    private final InstantiatorFactory instantiatorFactory;
    private Instantiator actionInstantiator;

    public NoIsolationWorkerFactory(WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor, AsyncWorkTracker workTracker, InstantiatorFactory instantiatorFactory) {
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
        this.workTracker = workTracker;
        this.instantiatorFactory = instantiatorFactory;
    }

    // Attaches the owning WorkerExecutor to this factory
    public void setWorkerExecutor(WorkerExecutor workerExecutor) {
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        services.add(WorkerExecutor.class, workerExecutor);
        actionInstantiator = instantiatorFactory.inject(services);
    }

    @Override
    public Worker getWorker(final DaemonForkOptions forkOptions) {
        return new Worker() {
            @Override
            public DefaultWorkResult execute(ActionExecutionSpec spec) {
                return execute(spec, workerLeaseRegistry.getCurrentWorkerLease(), buildOperationExecutor.getCurrentOperation());
            }

            @Override
            public DefaultWorkResult execute(final ActionExecutionSpec spec, WorkerLeaseRegistry.WorkerLease parentWorkerWorkerLease, final BuildOperationState parentBuildOperation) {
                WorkerLeaseRegistry.WorkerLeaseCompletion workerLease = parentWorkerWorkerLease.startChild();

                try {
                    return buildOperationExecutor.call(new CallableBuildOperation<DefaultWorkResult>() {
                        @Override
                        public DefaultWorkResult call(BuildOperationContext context) {
                            DefaultWorkResult result;
                            try {
                                WorkerProtocol<ActionExecutionSpec> workerServer = new DefaultWorkerServer(actionInstantiator);
                                result = workerServer.execute(spec);
                            } finally {
                                //TODO the async work tracker should wait for children of an operation to finish first.
                                //It should not be necessary to call it here.
                                workTracker.waitForCompletion(buildOperationExecutor.getCurrentOperation(), false);
                            }
                            return result;
                        }

                        @Override
                        public BuildOperationDescriptor.Builder description() {
                            return BuildOperationDescriptor.displayName(spec.getDisplayName()).parent(parentBuildOperation);
                        }
                    });
                } finally {
                    workerLease.leaseFinish();
                }
            }
        };
    }

    @Override
    public IsolationMode getIsolationMode() {
        return IsolationMode.NONE;
    }
}
