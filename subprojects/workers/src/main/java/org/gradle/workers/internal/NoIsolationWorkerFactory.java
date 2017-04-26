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

import org.gradle.internal.operations.BuildOperationContext;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.operations.CallableBuildOperation;
import org.gradle.internal.progress.BuildOperationDescriptor;
import org.gradle.internal.progress.BuildOperationState;
import org.gradle.internal.reflect.DirectInstantiator;
import org.gradle.internal.work.WorkerLeaseRegistry;

import java.io.File;

public class NoIsolationWorkerFactory implements WorkerFactory {
    private final WorkerLeaseRegistry workerLeaseRegistry;
    private final BuildOperationExecutor buildOperationExecutor;

    public NoIsolationWorkerFactory(WorkerLeaseRegistry workerLeaseRegistry, BuildOperationExecutor buildOperationExecutor) {
        this.workerLeaseRegistry = workerLeaseRegistry;
        this.buildOperationExecutor = buildOperationExecutor;
    }

    @Override
    public <T extends WorkSpec> Worker<T> getWorker(final Class<? extends WorkerProtocol<T>> workerImplementationClass, File workingDir, final DaemonForkOptions forkOptions) {
        return new Worker<T>() {
            @Override
            public DefaultWorkResult execute(T spec) {
                return execute(spec, workerLeaseRegistry.getCurrentWorkerLease(), buildOperationExecutor.getCurrentOperation());
            }

            @Override
            public DefaultWorkResult execute(final T spec, WorkerLeaseRegistry.WorkerLease parentWorkerWorkerLease, final BuildOperationState parentBuildOperation) {
                WorkerLeaseRegistry.WorkerLeaseCompletion workerLease = parentWorkerWorkerLease.startChild();

                try {
                    return buildOperationExecutor.call(new CallableBuildOperation<DefaultWorkResult>() {
                        @Override
                        public DefaultWorkResult call(BuildOperationContext context) {
                            return DirectInstantiator.INSTANCE.newInstance(workerImplementationClass).execute(spec);
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
}
