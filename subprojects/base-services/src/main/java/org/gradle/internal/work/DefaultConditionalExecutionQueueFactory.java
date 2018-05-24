/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.internal.work;

import org.gradle.concurrent.ParallelismConfiguration;
import org.gradle.internal.concurrent.ExecutorFactory;
import org.gradle.internal.resources.ResourceLockCoordinationService;

public class DefaultConditionalExecutionQueueFactory implements ConditionalExecutionQueueFactory {
    private final ParallelismConfiguration parallelismConfiguration;
    private final ExecutorFactory executorFactory;
    private final ResourceLockCoordinationService coordinationService;

    public DefaultConditionalExecutionQueueFactory(ParallelismConfiguration parallelismConfiguration, ExecutorFactory executorFactory, ResourceLockCoordinationService coordinationService) {
        this.parallelismConfiguration = parallelismConfiguration;
        this.executorFactory = executorFactory;
        this.coordinationService = coordinationService;
    }

    @Override
    public <T> ConditionalExecutionQueue<T> create(String displayName, Class<T> resultClass) {
        return new DefaultConditionalExecutionQueue<T>(displayName, parallelismConfiguration.getMaxWorkerCount(), executorFactory, coordinationService);
    }
}
