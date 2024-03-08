/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.operations;

import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.concurrent.DefaultExecutorFactory;
import org.gradle.internal.concurrent.DefaultParallelismConfiguration;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.work.WorkerLeaseService;
import org.gradle.test.fixtures.work.TestWorkerLeaseService;

public class BuildOperationExecutorSupport {
    public static Builder builder(int numThreads) {
        return builder(false, numThreads);
    }

    public static Builder builder(boolean parallelProjectExecution, int numThreads) {
        return builder(new DefaultParallelismConfiguration(parallelProjectExecution, numThreads));
    }

    public static Builder builder(ParallelismConfiguration parallelismConfiguration) {
        return new Builder(parallelismConfiguration);
    }

    public static class Builder {
        private final ParallelismConfiguration parallelismConfiguration;
        private BuildOperationTimeSupplier timeSupplier;
        private BuildOperationRunner runner;
        private WorkerLeaseService workerLeaseService;
        private BuildOperationQueueFactory queueFactory;
        private DefaultBuildOperationRunner.BuildOperationExecutionListenerFactory executionListenerFactory;
        private ExecutorFactory executorFactory;

        private Builder(ParallelismConfiguration parallelismConfiguration) {
            this.parallelismConfiguration = parallelismConfiguration;
        }

        public Builder withTimeSupplier(BuildOperationTimeSupplier timeSupplier) {
            this.timeSupplier = timeSupplier;
            return this;
        }

        public Builder withRunner(BuildOperationRunner runner) {
            this.runner = runner;
            return this;
        }

        public Builder withWorkerLeaseService(WorkerLeaseService workerLeaseService) {
            this.workerLeaseService = workerLeaseService;
            return this;
        }

        public Builder withQueueFactory(BuildOperationQueueFactory queueFactory) {
            this.queueFactory = queueFactory;
            return this;
        }

        public Builder withExecutionListenerFactory(DefaultBuildOperationRunner.BuildOperationExecutionListenerFactory executionListenerFactory) {
            this.executionListenerFactory = executionListenerFactory;
            return this;
        }

        public Builder withExecutorFactory(ExecutorFactory executorFactory) {
            this.executorFactory = executorFactory;
            return this;
        }

        public BuildOperationExecutor build() {
            WorkerLeaseService workerLeaseService = this.workerLeaseService != null
                ? this.workerLeaseService
                : new TestWorkerLeaseService();
            BuildOperationQueueFactory queueFactory = this.queueFactory != null
                ? this.queueFactory
                : new DefaultBuildOperationQueueFactory(workerLeaseService);
            ExecutorFactory executorFactory = this.executorFactory != null
                ? this.executorFactory
                : new DefaultExecutorFactory();

            return new DefaultBuildOperationExecutor(
                buildRunner(),
                CurrentBuildOperationRef.instance(),
                queueFactory,
                executorFactory,
                parallelismConfiguration);
        }

        private BuildOperationRunner buildRunner() {
            BuildOperationRunner runner;
            if (this.runner == null) {
                BuildOperationTimeSupplier timeSupplier = this.timeSupplier != null
                    ? this.timeSupplier
                    : System::currentTimeMillis;
                DefaultBuildOperationRunner.BuildOperationExecutionListenerFactory executionListenerFactory = this.executionListenerFactory != null
                    ? this.executionListenerFactory
                    : () -> DefaultBuildOperationRunner.BuildOperationExecutionListener.NO_OP;

                runner = new DefaultBuildOperationRunner(
                    CurrentBuildOperationRef.instance(),
                    timeSupplier,
                    new DefaultBuildOperationIdFactory(),
                    executionListenerFactory
                );
            } else {
                runner = this.runner;
            }
            return runner;
        }
    }
}
