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
import org.gradle.internal.reflect.problems.ValidationProblemId;
import org.gradle.internal.reflect.validation.Severity;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.TextUtil;

import java.io.File;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public class LocalTaskNodeExecutor implements NodeExecutor {

    private final ExecutionNodeAccessHierarchy outputHierarchy;

    public LocalTaskNodeExecutor(ExecutionNodeAccessHierarchy outputHierarchy) {
        this.outputHierarchy = outputHierarchy;
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
            ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy inputHierarchy = context.getService(ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy.class);
            TaskExecutionContext ctx = new DefaultTaskExecutionContext(
                localTaskNode,
                localTaskNode.getTaskProperties(),
                localTaskNode.getValidationContext(),
                (historyMaintained, typeValidationContext) -> detectMissingDependencies(localTaskNode, historyMaintained, inputHierarchy, typeValidationContext)
            );
            TaskExecuter taskExecuter = context.getService(TaskExecuter.class);
            taskExecuter.execute(task, state, ctx);
            localTaskNode.getPostAction().execute(task);
            return true;
        } else {
            return false;
        }
    }

    private void detectMissingDependencies(LocalTaskNode node, boolean historyMaintained, ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy inputHierarchy, TypeValidationContext validationContext) {
        for (String outputPath : node.getMutationInfo().outputPaths) {
            inputHierarchy.getNodesAccessing(outputPath).stream()
                .filter(consumerNode -> hasNoSpecifiedOrder(node, consumerNode))
                .filter(LocalTaskNodeExecutor::isEnabled)
                .forEach(consumerWithoutDependency -> collectValidationProblem(
                    node,
                    consumerWithoutDependency,
                    validationContext,
                    outputPath)
                );
        }
        Set<String> taskInputs = new LinkedHashSet<>();
        Set<FilteredTree> filteredFileTreeTaskInputs = new LinkedHashSet<>();
        node.getTaskProperties().getInputFileProperties()
            .forEach(spec -> {
                try {
                    spec.getPropertyFiles().visitStructure(new FileCollectionStructureVisitor() {
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
                    });
                } catch (Exception e) {
                    if (historyMaintained) {
                        // We would later try to snapshot the inputs anyway, no need to suppress the exception
                        throw e;
                    } else {
                        validationContext.visitPropertyProblem(problem ->
                            problem.withId(ValidationProblemId.UNRESOLVABLE_INPUT)
                                .forProperty(spec.getPropertyName())
                                .reportAs(Severity.WARNING)
                                .withDescription(() -> String.format("cannot be resolved:%n%s%n", TextUtil.indent(e.getMessage(), "  ")))
                                .happensBecause("An input file collection couldn't be resolved, making it impossible to determine task inputs")
                                .addPossibleSolution("Consider using Task.dependsOn instead")
                                .documentedAt("validation_problems", "unresolvable_input")
                        );
                    }
                }
            });
        inputHierarchy.recordNodeAccessingLocations(node, taskInputs);
        for (String locationConsumedByThisTask : taskInputs) {
            collectValidationProblemsForConsumer(node, validationContext, locationConsumedByThisTask, outputHierarchy.getNodesAccessing(locationConsumedByThisTask));
        }
        for (FilteredTree filteredFileTreeInput : filteredFileTreeTaskInputs) {
            Spec<FileTreeElement> spec = filteredFileTreeInput.getPatterns().getAsSpec();
            inputHierarchy.recordNodeAccessingFileTree(node, filteredFileTreeInput.getRoot(), spec);
            collectValidationProblemsForConsumer(
                node,
                validationContext,
                filteredFileTreeInput.getRoot(),
                outputHierarchy.getNodesAccessing(filteredFileTreeInput.getRoot(), spec)
            );
        }
    }

    private void collectValidationProblemsForConsumer(LocalTaskNode consumer, TypeValidationContext validationContext, String locationConsumedByThisTask, Collection<Node> producers) {
        producers.stream()
            .filter(producerNode -> hasNoSpecifiedOrder(producerNode, consumer))
            .filter(LocalTaskNodeExecutor::isEnabled)
            .forEach(producerWithoutDependency -> collectValidationProblem(
                producerWithoutDependency,
                consumer,
                validationContext,
                locationConsumedByThisTask
            ));
    }

    private static boolean isEnabled(Node node) {
        if (node instanceof LocalTaskNode) {
            TaskInternal task = ((LocalTaskNode) node).getTask();
            return task.getOnlyIf().isSatisfiedBy(task);
        }
        return false;
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
        addHardSuccessorTasksToQueue(consumer, seenNodes, queue);
        while (!queue.isEmpty()) {
            Node dependency = queue.removeFirst();
            if (dependency == producer) {
                return false;
            }
            addHardSuccessorTasksToQueue(dependency, seenNodes, queue);
        }
        return true;
    }

    private static void addHardSuccessorTasksToQueue(Node node, Set<Node> seenNodes, Queue<Node> queue) {
        node.getHardSuccessors().forEach(successor -> {
            // We are searching for dependencies between tasks, so we can skip everything which is not a task when searching.
            // For example we can skip all the transform nodes between two task nodes.
            if (successor instanceof TaskNode) {
                if (seenNodes.add(successor)) {
                    queue.add(successor);
                }
            } else {
                addHardSuccessorTasksToQueue(successor, seenNodes, queue);
            }
        });
    }

    private void collectValidationProblem(Node producer, Node consumer, TypeValidationContext validationContext, String consumerProducerPath) {
        validationContext.visitPropertyProblem(problem ->
            problem.withId(ValidationProblemId.IMPLICIT_DEPENDENCY)
                .reportAs(Severity.WARNING)
                .withDescription(() -> "Gradle detected a problem with the following location: '" + consumerProducerPath + "'")
                .happensBecause(() -> String.format("Task '%s' uses this output of task '%s' without declaring an explicit or implicit dependency. "
                        + "This can lead to incorrect results being produced, depending on what order the tasks are executed",
                    consumer,
                    producer
                ))
                .addPossibleSolution(() -> "Declare task '" + producer + "' as an input of '" + consumer + "'")
                .addPossibleSolution(() -> "Declare an explicit dependency on '" + producer + "' from '" + consumer + "' using Task#dependsOn")
                .addPossibleSolution(() -> "Declare an explicit dependency on '" + producer + "' from '" + consumer + "' using Task#mustRunAfter")
                .documentedAt("validation_problems", "implicit_dependency")
                .typeIsIrrelevantInErrorMessage()
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
