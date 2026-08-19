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
import org.gradle.api.problems.internal.GradleCoreProblemGroup;
import org.gradle.api.specs.Spec;
import org.gradle.internal.deprecation.DeprecationLogger;
import org.gradle.internal.reflect.validation.TypeValidationContext;
import org.gradle.util.Path;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Queue;
import java.util.Set;

import static org.gradle.internal.deprecation.Documentation.userManual;

/**
 * Detects missing task dependencies between tasks producing and consuming file locations.
 *
 * <p>
 * The detector is triggered both when a task produces an output location and when a task consumes an input location.
 * This is to ensure that we detect an overlap regardless of what order producer and consumer runs in.
 * </p>
 *
 * <p>
 * An overlap between inputs and outputs requiring a declared dependency exists between tasks
 * if <em>consumer</em> consumes any file produced by <em>producer.</em>
 * This includes <em>consumer</em> consuming a filtered set of the produced files, or
 * consuming a parent directory of the produced output.
 * </p>
 */
public class MissingTaskDependencyDetector {
    private final ExecutionNodeAccessHierarchy outputHierarchy;
    private final ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy inputHierarchy;

    public MissingTaskDependencyDetector(ExecutionNodeAccessHierarchy outputHierarchy, ExecutionNodeAccessHierarchies.InputNodeAccessHierarchy inputHierarchy) {
        this.outputHierarchy = outputHierarchy;
        this.inputHierarchy = inputHierarchy;
    }

    /**
     * Records the given node accessing the given input location and checks if there are any nodes producing the location that the node does not depend on.
     */
    public void visitUnfilteredInputLocation(LocalTaskNode node, TypeValidationContext validationContext, String location) {
        inputHierarchy.recordNodeAccessingLocation(node, location);
        collectValidationProblemsForConsumer(node, validationContext, location, outputHierarchy.getNodesAccessing(location));
    }

    /**
     * Records the given node accessing the given input location with a filter, and checks if there are any nodes producing the location that the node does not depend on.
     */
    public void visitFilteredInputLocation(LocalTaskNode node, TypeValidationContext validationContext, String location, Spec<FileTreeElement> spec) {
        inputHierarchy.recordNodeAccessingFileTree(node, location, spec);
        collectValidationProblemsForConsumer(node, validationContext, location, outputHierarchy.getNodesAccessing(location, spec));
    }

    /**
     * Records the given node producing the given output location and checks if there are any nodes consuming the location without declaring a dependency on the producer.
     */
    public void visitOutputLocation(LocalTaskNode node, TypeValidationContext validationContext, String location) {
        // TODO We should have already recorded outputs in ResolveMutationsNode, but we should probably do it here instead
        collectValidationProblemsForProducer(node, validationContext, location, inputHierarchy.getNodesAccessing(location));
    }

    private static void collectValidationProblemsForConsumer(LocalTaskNode consumer, TypeValidationContext validationContext, String locationConsumedByThisTask, Collection<Node> producers) {
        for (Node producerNode : producers) {
            if (!hasNoSpecifiedOrder(producerNode, consumer)) {
                continue;
            }
            LocalTaskNode producerWithoutDependency = asEnabledTaskNode(producerNode);
            if (producerWithoutDependency != null) {
                collectValidationProblem(
                    producerWithoutDependency,
                    consumer,
                    validationContext,
                    locationConsumedByThisTask
                );
            }
        }
    }

    private static void collectValidationProblemsForProducer(LocalTaskNode node, TypeValidationContext validationContext, String outputPath, Collection<Node> consumers) {
        for (Node consumerNode : consumers) {
            if (!hasNoSpecifiedOrder(node, consumerNode)) {
                continue;
            }
            LocalTaskNode consumerWithoutDependency = asEnabledTaskNode(consumerNode);
            if (consumerWithoutDependency != null) {
                collectValidationProblem(
                    node,
                    consumerWithoutDependency,
                    validationContext,
                    outputPath
                );
            }
        }
    }

    /**
     * Returns the given node as a task node, or null when it is not a task node, or it
     * is not enabled.
     */
    private static @Nullable LocalTaskNode asEnabledTaskNode(Node node) {
        if (!(node instanceof LocalTaskNode)) {
            return null;
        }
        LocalTaskNode taskNode = (LocalTaskNode) node;
        TaskInternal task = taskNode.getTask();
        return task.getOnlyIf().isSatisfiedBy(task) ? taskNode : null;
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
            if (successor instanceof TaskInAnotherBuild) {
                // A reference to a task in another build is a proxy for the task node in the other build's work graph.
                Node targetNode = ((TaskInAnotherBuild) successor).getTargetNode();
                if (seenNodes.add(targetNode)) {
                    queue.add(targetNode);
                }
            } else if (successor instanceof TaskNode || successor instanceof OrdinalNode) {
                if (seenNodes.add(successor)) {
                    queue.add(successor);
                }
            } else {
                addHardSuccessorTasksToQueue(successor, seenNodes, queue);
            }
        });
    }

    private static final String IMPLICIT_DEPENDENCY = "IMPLICIT_DEPENDENCY";

    private static void collectValidationProblem(LocalTaskNode producer, LocalTaskNode consumer, TypeValidationContext validationContext, String consumerProducerPath) {
        if (areInDifferentBuilds(producer, consumer)) {
            // Historically, we did not detect overlapping outputs between nodes in different builds.
            // Emit a deprecation warning instead of a failure until Gradle 10.
            // In Gradle 10, we can remove this branch entirely and fall back to the problem-emitting branch below.
            DeprecationLogger.deprecateAction("Producing a file in one build and consuming it in another build without declaring an explicit dependency")
                .withContext(String.format(
                    "Gradle detected a problem with the following location: '%s'. Task '%s' uses this output of task '%s' without declaring an explicit or implicit dependency. "
                        + "This can lead to incorrect results being produced, depending on what order the tasks are executed.",
                    consumerProducerPath,
                    consumer,
                    producer
                ))
                .withProblemIdDisplayName("Implicit dependency between tasks in different builds")
                .withProblemId("implicit-dependency-between-tasks-in-different-builds")
                .willBecomeAnErrorInGradle10()
                .withUserManual("validation_problems", IMPLICIT_DEPENDENCY.toLowerCase(Locale.ROOT))
                .nagUser();
            return;
        }
        validationContext.visitPropertyError(problem ->
            problem.id(TextUtil.screamingSnakeToKebabCase(IMPLICIT_DEPENDENCY), "Property has implicit dependency", GradleCoreProblemGroup.validation().property()) // TODO (donat) missing test coverage
                .contextualLabel("Gradle detected a problem with the following location: '" + consumerProducerPath + "'")
                .documentedAt(userManual("validation_problems", IMPLICIT_DEPENDENCY.toLowerCase(Locale.ROOT)))
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

    private static boolean areInDifferentBuilds(LocalTaskNode producer, LocalTaskNode consumer) {
        return !buildPathOf(producer).equals(buildPathOf(consumer));
    }

    private static Path buildPathOf(LocalTaskNode node) {
        return node.getTask().getTaskIdentity().getProjectIdentity().getBuildPath();
    }

}
