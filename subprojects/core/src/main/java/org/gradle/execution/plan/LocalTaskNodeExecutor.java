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

import org.gradle.api.file.FileTreeElement;
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
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.reflect.TypeValidationContext;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class LocalTaskNodeExecutor implements NodeExecutor {

    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchy inputHierarchy;

    public LocalTaskNodeExecutor(ExecutionNodeAccessHierarchy outputHierarchy, ExecutionNodeAccessHierarchy inputHierarchy) {
        this.outputHierarchy = outputHierarchy;
        this.inputHierarchy = inputHierarchy;
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
            inputHierarchy.getNodesAccessing(outputPath).stream()
                .filter(consumerNode -> hasNoSpecifiedOrder(node, consumerNode))
                .forEach(consumerWithoutDependency -> collectValidationProblem(node, consumerWithoutDependency, validationContext));
        }
        Set<String> taskInputs = new LinkedHashSet<>();
        Set<FilteredTree> filteredFileTreeTaskInputs = new LinkedHashSet<>();
        node.getTaskProperties().getInputFileProperties()
            .forEach(spec -> spec.getPropertyFiles().visitStructure(new FileCollectionStructureVisitor() {
                @Override
                public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                    contents.forEach(location -> taskInputs.add(location.getAbsolutePath()));
                }

                @Override
                public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    fileTree.forEach(location -> taskInputs.add(location.getAbsolutePath()));
                }

                @Override
                public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                    if (patterns.isEmpty()) {
                        taskInputs.add(root.getAbsolutePath());
                    } else {
                        filteredFileTreeTaskInputs.add(new FilteredTree(root.getAbsolutePath(), patterns));
                    }
                }

                @Override
                public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    taskInputs.add(file.getAbsolutePath());
                }
            }));
        inputHierarchy.recordNodeAccessingLocations(node, taskInputs);
        for (String locationConsumedByThisTask : taskInputs) {
            outputHierarchy.getNodesAccessing(locationConsumedByThisTask).stream()
                .filter(producerNode -> hasNoSpecifiedOrder(producerNode, node))
                .forEach(producerWithoutDependency -> collectValidationProblem(producerWithoutDependency, node, validationContext));
        }
        for (FilteredTree filteredFileTreeInput : filteredFileTreeTaskInputs) {
            Spec<FileTreeElement> spec = filteredFileTreeInput.getPatterns().getAsSpec();
            inputHierarchy.recordNodeAccessingFileTree(node, filteredFileTreeInput.getRoot(), spec);
            outputHierarchy.getNodesAccessing(filteredFileTreeInput.getRoot(), spec).stream()
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
        Set<Node> seenNodes = new HashSet<>();
        consumer.getHardSuccessors().forEach(successor -> {
            if (seenNodes.add(successor)) {
                queue.add(successor);
            }
        });
        while (!queue.isEmpty()) {
            Node dependency = queue.removeFirst();
            if (dependency == producer) {
                return false;
            }
            dependency.getHardSuccessors().forEach(node -> {
                if (seenNodes.add(node)) {
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
            String.format("Task '%s' uses the output of task '%s', without declaring an explicit dependency (using Task.dependsOn() or Task.mustRunAfter()) or an implicit dependency (declaring task '%s' as an input). This can lead to incorrect results being produced, depending on what order the tasks are executed", consumer, producer, producer)
        );
    }

    private static class FilteredTree {
        private final String root;
        private final PatternSet patterns;

        private FilteredTree(String root, PatternSet patterns) {
            this.root = root;
            this.patterns = patterns;
        }

        public String getRoot() {
            return root;
        }

        public PatternSet getPatterns() {
            return patterns;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FilteredTree that = (FilteredTree) o;
            return root.equals(that.root) && patterns.equals(that.patterns);
        }

        @Override
        public int hashCode() {
            return Objects.hash(root, patterns);
        }

    }
}
