/*
 * Copyright 2022 the original author or authors.
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
import org.gradle.api.problems.Severity;
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.specs.Spec;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.internal.TextUtil;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

import static org.gradle.internal.deprecation.Documentation.userManual;

public class MissingTaskDependencyDetector {
    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy inputHierarchy;

    public MissingTaskDependencyDetector(ExecutionNodeAccessHierarchy outputHierarchy, ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy inputHierarchy) {
        this.outputHierarchy = outputHierarchy;
        this.inputHierarchy = inputHierarchy;
    }

    public void detectMissingDependencies(LocalTaskNode node, TypeValidationContext validationContext) {
        for (String outputPath : node.getMutationInfo().getOutputPaths()) {
            inputHierarchy.getNodesAccessing(outputPath).stream()
                .filter(consumerNode -> hasNoSpecifiedOrder(node, consumerNode))
                .filter(MissingTaskDependencyDetector::isEnabled)
                .forEach(consumerWithoutDependency -> collectValidationProblem(
                    node,
                    consumerWithoutDependency,
                    validationContext,
                    outputPath)
                );
        }
    }

    public void visitUnfilteredInputLocation(LocalTaskNode node, TypeValidationContext validationContext, String location) {
        inputHierarchy.recordNodeAccessingLocation(node, location);
        collectValidationProblemsForConsumer(node, validationContext, location, outputHierarchy.getNodesAccessing(location));
    }

    public void visitFilteredInputLocation(LocalTaskNode node, TypeValidationContext validationContext, String location, Spec<FileTreeElement> spec) {
        inputHierarchy.recordNodeAccessingFileTree(node, location, spec);
        collectValidationProblemsForConsumer(node, validationContext, location, outputHierarchy.getNodesAccessing(location, spec));
    }

    private static void collectValidationProblemsForConsumer(LocalTaskNode consumer, TypeValidationContext validationContext, String locationConsumedByThisTask, Collection<Node> producers) {
        producers.stream()
            .filter(producerNode -> hasNoSpecifiedOrder(producerNode, consumer))
            .filter(MissingTaskDependencyDetector::isEnabled)
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
    // And for that case, there will be a cyclic dependency between the 'analyze' and the 'format' task if we only take output/input locations into account.
    // Therefore, we currently allow these kind of missing dependencies, as long as any order has been specified.
    // See https://github.com/gradle/gradle/issues/15616.
    private static boolean hasNoSpecifiedOrder(Node producerNode, Node consumerNode) {
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
            // For example, we can skip all the transform nodes between two task nodes.
            if (successor instanceof TaskNode || successor instanceof OrdinalNode) {
                if (seenNodes.add(successor)) {
                    queue.add(successor);
                }
            } else {
                addHardSuccessorTasksToQueue(successor, seenNodes, queue);
            }
        });
    }

    private static final String IMPLICIT_DEPENDENCY = "IMPLICIT_DEPENDENCY";

    private static void collectValidationProblem(Node producer, Node consumer, TypeValidationContext validationContext, String consumerProducerPath) {
        validationContext.visitPropertyProblem(problem ->
            problem.id(TextUtil.screamingSnakeToKebabCase(IMPLICIT_DEPENDENCY), "Property has implicit dependency", GradleCoreProblemGroup.validation().property()) // TODO (donat) missing test coverage
                .contextualLabel("Gradle detected a problem with the following location: '" + consumerProducerPath + "'")
                .documentedAt(userManual("validation_problems", IMPLICIT_DEPENDENCY.toLowerCase(Locale.ROOT)))
                .severity(Severity.ERROR)
                .details(String.format("Task '%s' uses this output of task '%s' without declaring an explicit or implicit dependency. "
                        + "This can lead to incorrect results being produced, depending on what order the tasks are executed",
                    consumer,
                    producer
                ))
                .solution("Declare task '" + producer + "' as an input of '" + consumer + "'")
                .solution("Declare an explicit dependency on '" + producer + "' from '" + consumer + "' using Task#dependsOn")
                .solution("Declare an explicit dependency on '" + producer + "' from '" + consumer + "' using Task#mustRunAfter")
        );
    }
}
