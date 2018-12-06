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

import org.gradle.api.execution.TaskExecutionGraph;
import org.gradle.api.execution.TaskExecutionGraphListener;
import org.gradle.api.internal.project.WorkIdentity;
import org.gradle.execution.plan.Node;
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

class OperationDependenciesResolver implements TaskExecutionGraphListener {

    private final Map<Path, TaskExecutionGraphInternal> taskExecutionGraphs = new HashMap<>();
    private final List<OperationDependencyLookup> lookups = new ArrayList<>();

    void addLookup(OperationDependencyLookup lookup) {
        lookups.add(lookup);
    }

    @Override
    public void graphPopulated(@Nonnull TaskExecutionGraph taskExecutionGraph) {
        Path buildPath = ((TaskExecutionGraphInternal) taskExecutionGraph).getRootProject().getGradle().getIdentityPath();
        taskExecutionGraphs.put(buildPath, (TaskExecutionGraphInternal) taskExecutionGraph);
    }

    Set<InternalOperationDescriptor> resolveDependencies(Path buildPath, WorkIdentity workIdentity) {
        return taskExecutionGraphs.get(buildPath)
            .getNode(workIdentity)
            .getDependencySuccessors().stream()
            .map(this::lookupExistingOperationDescriptor)
            .filter(Objects::nonNull)
            .collect(toCollection(LinkedHashSet::new));
    }

    private InternalOperationDescriptor lookupExistingOperationDescriptor(Node node) {
        return lookups.stream()
            .map(entry -> entry.lookupExistingOperationDescriptor(node))
            .filter(Objects::nonNull)
            .findFirst()
            .orElse(null);
    }

}
