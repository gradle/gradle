/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.execution.durations;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.internal.project.taskfactory.TaskIdentity;
import org.gradle.execution.plan.LocalTaskNode;
import org.gradle.execution.plan.Node;
import org.gradle.execution.plan.QueryableExecutionPlan;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.ServiceScope;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.Math.max;

/**
 * Estimates how long the work of the execution plan of a build will take, from the recorded task durations.
 */
@ServiceScope(Scope.Build.class)
public class ExecutionTimeEstimates {
    private final TaskDurationHistory history;
    private volatile Map<Long, ExecutionTimeEstimate> taskEstimates = ImmutableMap.of();

    public ExecutionTimeEstimates(TaskDurationHistory history) {
        this.history = history;
    }

    /**
     * Estimates the work of the given plan, and remembers the estimate of each of its tasks.
     * Returns null when any work in the plan has no recorded duration.
     */
    @Nullable
    public ExecutionTimeEstimate estimatePlan(QueryableExecutionPlan contents) {
        taskEstimates = ImmutableMap.of();
        if (contents.getTasks().size() != contents.size()) {
            // Work other than tasks, such as artifact transforms, has no recorded durations
            return null;
        }

        List<Node> nodes = contents.getScheduledNodes().getScheduledNodes();
        Map<Node, Long> durations = new HashMap<>();
        long totalDuration = 0;
        for (Node node : nodes) {
            long duration = 0;
            if (node instanceof LocalTaskNode) {
                TaskDurations recorded = history.getDurations(taskIdentity(node).getBuildTreePath());
                if (recorded == null) {
                    return null;
                }
                duration = recorded.getAverage().toMillis();
                totalDuration += duration;
            }
            durations.put(node, duration);
        }

        // Nodes are scheduled before the nodes that run after them, so visit them in reverse
        Map<Node, Long> chains = new HashMap<>();
        ImmutableMap.Builder<Long, ExecutionTimeEstimate> tasks = ImmutableMap.builder();
        long criticalPath = 0;
        for (Node node : Lists.reverse(nodes)) {
            long following = 0;
            for (Node dependent : Iterables.concat(node.getDependencyPredecessors(), node.getFinalizers())) {
                Long chain = chains.get(dependent);
                if (chain != null) {
                    following = max(following, chain);
                }
            }
            long duration = durations.get(node);
            long chain = duration + following;
            chains.put(node, chain);
            criticalPath = max(criticalPath, chain);
            if (node instanceof LocalTaskNode) {
                tasks.put(taskIdentity(node).getId(), new ExecutionTimeEstimate(duration, chain));
            }
        }
        taskEstimates = tasks.build();
        return new ExecutionTimeEstimate(totalDuration, criticalPath);
    }

    /**
     * Returns the estimate of the given task of the most recently estimated plan, or null when it has none.
     */
    @Nullable
    public ExecutionTimeEstimate estimateTask(TaskIdentity<?> taskIdentity) {
        return taskEstimates.get(taskIdentity.getId());
    }

    private static TaskIdentity<?> taskIdentity(Node node) {
        return ((LocalTaskNode) node).getTask().getTaskIdentity();
    }
}
