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

package org.gradle.execution.plan;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionStructureVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.FileSystemMirroringFileTree;
import org.gradle.api.internal.tasks.NodeExecutionContext;
import org.gradle.api.internal.tasks.TaskExecuter;
import org.gradle.api.internal.tasks.TaskExecutionContext;
import org.gradle.api.internal.tasks.TaskStateInternal;
import org.gradle.api.internal.tasks.execution.DefaultTaskExecutionContext;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.reflect.TypeValidationContext;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.Set;

public class LocalTaskNodeExecutor implements NodeExecutor {

    private final RelatedLocations producedLocations;
    private final RelatedLocations consumedLocations;

    public LocalTaskNodeExecutor(RelatedLocations producedLocations, RelatedLocations consumedLocations) {
        this.producedLocations = producedLocations;
        this.consumedLocations = consumedLocations;
    }

    @Override
    public boolean execute(Node node, NodeExecutionContext context) {
        if (node instanceof LocalTaskNode) {
            LocalTaskNode localTaskNode = (LocalTaskNode) node;
            TaskInternal task = localTaskNode.getTask();
            TaskStateInternal state = task.getState();
            if (state.getExecuted()) {
                // Task has already been run. This can happen when the owning build is used both at configuration time and execution time
                // This should move earlier in task scheduling, so that a worker thread does not even bother trying to run this task
                return true;
            }
            TaskExecutionContext ctx = new DefaultTaskExecutionContext(
                localTaskNode,
                localTaskNode.getTaskProperties(),
                localTaskNode.getValidationContext(),
                typeValidationContext -> detectMissingDependencies(localTaskNode, typeValidationContext)
            );
            TaskExecuter taskExecuter = context.getService(TaskExecuter.class);
            taskExecuter.execute(task, state, ctx);
            localTaskNode.getPostAction().execute(task);
            return true;
        } else {
            return false;
        }
    }

    private void detectMissingDependencies(LocalTaskNode node, TypeValidationContext validationContext) {
        for (String outputPath : node.getMutationInfo().outputPaths) {
            consumedLocations.getNodesRelatedTo(outputPath).stream()
                .filter(consumerNode -> hasNoSpecifiedOrder(node, consumerNode))
                .forEach(consumerWithoutDependency -> collectValidationProblem(node, consumerWithoutDependency, validationContext));
        }
        Set<String> locationsConsumedByThisTask = new LinkedHashSet<>();
        node.getTaskProperties().getInputFileProperties()
            .forEach(spec -> spec.getPropertyFiles().visitStructure(new FileCollectionStructureVisitor() {
                @Override
                public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                    contents.forEach(location -> locationsConsumedByThisTask.add(location.getAbsolutePath()));
                }

                @Override
                public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    fileTree.forEach(location -> locationsConsumedByThisTask.add(location.getAbsolutePath()));
                }

                @Override
                public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                    locationsConsumedByThisTask.add(root.getAbsolutePath());
                }

                @Override
                public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    locationsConsumedByThisTask.add(file.getAbsolutePath());
                }
            }));
        consumedLocations.recordRelatedToNode(node, locationsConsumedByThisTask);
        for (String locationConsumedByThisTask : locationsConsumedByThisTask) {
            producedLocations.getNodesRelatedTo(locationConsumedByThisTask).stream()
                .filter(producerNode -> hasNoSpecifiedOrder(producerNode, node))
                .forEach(producerWithoutDependency -> collectValidationProblem(producerWithoutDependency, node, validationContext));
        }
    }

    // In a perfect world, the consumer should depend on the producer.
    // Though we still don't have a good solution for the code linter and formatter use-case.
    // And for that case, there will be a cyclic dependency between the analyze and the format task if we only take output/input locations into account.
    // Therefore, we currently allow these kind of missing dependencies, as long as any order has been specified.
    // See https://github.com/gradle/gradle/issues/15616.
    private boolean hasNoSpecifiedOrder(Node producerNode, Node consumerNode) {
        return missesDependency(producerNode, consumerNode) && missesDependency(consumerNode, producerNode);
    }

    private static boolean missesDependency(Node producer, Node consumer) {
        if (consumer == producer) {
            return false;
        }
        // This is a performance optimization to short-cut the search for a dependency if there is a direct dependency.
        // We use `getDependencySuccessors()` instead of `getAllDependencySuccessors()`, since the former is a Set while the latter is only an Iterable.
        if (consumer.getDependencySuccessors().contains(producer)) {
            return false;
        }
        // Do a breadth first search for any dependency
        Deque<Node> queue = new ArrayDeque<>();
        consumer.getHardSuccessors().forEach(queue::add);
        while (!queue.isEmpty()) {
            Node dependency = queue.removeFirst();
            if (dependency == producer) {
                return false;
            }
            dependency.getHardSuccessors().forEach(node -> {
                // Using the queue itself for deduplication should be fast enough
                // since the queue should normally be pretty short.
                if (!queue.contains(node)) {
                    queue.add(node);
                }
            });
        }
        return true;
    }

    private void collectValidationProblem(Node producer, Node consumer, TypeValidationContext validationContext) {
        TypeValidationContext.Severity severity = TypeValidationContext.Severity.WARNING;
        validationContext.visitPropertyProblem(
            severity,
            String.format("Task '%s' uses the output of task '%s', without declaring an explicit dependency (using dependsOn or mustRunAfter) or an implicit dependency (declaring task '%s' as an input). This can lead to incorrect results being produced, depending on what order the tasks are executed", consumer, producer, producer)
        );
    }
}
