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

package org.gradle.tooling.internal.provider.runner;

import org.gradle.api.Task;
import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.execution.plan.ExecutionDependencies;
import org.gradle.execution.plan.TransformationNodeIdentifier;
import org.gradle.execution.taskgraph.TaskExecutionGraphInternal;
import org.gradle.tooling.internal.protocol.events.InternalOperationDescriptor;
import org.gradle.util.Path;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static java.util.stream.Collectors.toCollection;

class OperationDependenciesProvider implements TaskExecutionGraphListener {

    private final Map<Path, TaskExecutionGraphInternal> taskExecutionGraphs = new HashMap<>();
    private final List<OperationDependenciesLookup> lookups = new ArrayList<>();

    void addLookup(OperationDependenciesLookup lookup) {
        lookups.add(lookup);
    }

    @Override
    public void graphPopulated(@Nonnull TaskExecutionGraph taskExecutionGraph) {
        Path buildPath = ((TaskExecutionGraphInternal) taskExecutionGraph).getRootProject().getGradle().getIdentityPath();
        taskExecutionGraphs.put(buildPath, (TaskExecutionGraphInternal) taskExecutionGraph);
    }

    Set<InternalOperationDescriptor> computeTaskDependencies(Task task) {
        Path buildPath = ((ProjectInternal) task.getProject()).getGradle().getIdentityPath();
        TaskExecutionGraphInternal taskExecutionGraph = taskExecutionGraphs.get(buildPath);
        return lookupExistingOperationDescriptors(taskExecutionGraph.getExecutionDependencies(task));
    }

    Set<InternalOperationDescriptor> computeTransformDependencies(Path buildPath, long transformationId) {
        TaskExecutionGraphInternal taskExecutionGraph = taskExecutionGraphs.get(buildPath);
        return lookupExistingOperationDescriptors(taskExecutionGraph.getExecutionDependencies(new DefaultTransformationNodeIdentifier(transformationId)));
    }

    private Set<InternalOperationDescriptor> lookupExistingOperationDescriptors(ExecutionDependencies dependencies) {
        return lookups.stream()
            .flatMap(entry -> entry.lookupExistingOperationDescriptors(dependencies))
            .filter(Objects::nonNull)
            .collect(toCollection(LinkedHashSet::new));
    }

    private static class DefaultTransformationNodeIdentifier implements TransformationNodeIdentifier {

        private final long transformationId;

        public DefaultTransformationNodeIdentifier(long transformationId) {
            this.transformationId = transformationId;
        }

        @Override
        public long getUniqueId() {
            return transformationId;
        }

    }

}
