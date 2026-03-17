/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.execution.plan;

import org.gradle.internal.buildtree.BuildModelParameters;
import org.gradle.internal.operations.BuildOperationExecutor;
import org.gradle.internal.resources.ResourceLockCoordinationService;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.gradle.internal.work.WorkerLeaseService;

@ServiceScope(Scope.Build.class)
public class ExecutionPlanFactory {
    private final String displayName;
    private final TaskNodeFactory taskNodeFactory;
    private final OrdinalGroupFactory ordinalGroupFactory;
    private final TaskDependencyResolver dependencyResolver;
    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchy destroyableHierarchy;
    private final ResourceLockCoordinationService lockCoordinationService;
    private final WorkerLeaseService workerLeaseService;
    private final BuildOperationExecutor buildOperationExecutor;
    private final boolean parallelTaskDependencyResolution;

    public ExecutionPlanFactory(
        String displayName,
        TaskNodeFactory taskNodeFactory,
        OrdinalGroupFactory ordinalGroupFactory,
        TaskDependencyResolver dependencyResolver,
        ExecutionNodeAccessHierarchy outputHierarchy,
        ExecutionNodeAccessHierarchy destroyableHierarchy,
        ResourceLockCoordinationService lockCoordinationService,
        WorkerLeaseService workerLeaseService,
        BuildOperationExecutor buildOperationExecutor,
        BuildModelParameters buildModelParameters
    ) {
        this.displayName = displayName;
        this.taskNodeFactory = taskNodeFactory;
        this.ordinalGroupFactory = ordinalGroupFactory;
        this.dependencyResolver = dependencyResolver;
        this.outputHierarchy = outputHierarchy;
        this.destroyableHierarchy = destroyableHierarchy;
        this.lockCoordinationService = lockCoordinationService;
        this.workerLeaseService = workerLeaseService;
        this.buildOperationExecutor = buildOperationExecutor;
        this.parallelTaskDependencyResolution = buildModelParameters.isParallelProjectConfiguration();
    }

    public ExecutionPlan createPlan() {
        return new DefaultExecutionPlan(displayName, taskNodeFactory, ordinalGroupFactory, dependencyResolver, outputHierarchy, destroyableHierarchy, lockCoordinationService, workerLeaseService, buildOperationExecutor, parallelTaskDependencyResolution);
    }
}
