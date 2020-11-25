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
import org.gradle.internal.deprecation.DeprecationLogger;

import java.io.File;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.Set;

public class LocalTaskNodeExecutor implements NodeExecutor {

    private final ConsumedAndProducedLocations consumedAndProducedLocations;

    public LocalTaskNodeExecutor(ConsumedAndProducedLocations consumedAndProducedLocations) {
        this.consumedAndProducedLocations = consumedAndProducedLocations;
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
            TaskExecutionContext ctx = new DefaultTaskExecutionContext(localTaskNode, localTaskNode.getTaskProperties(), () -> detectMissingDependencies(localTaskNode));
            TaskExecuter taskExecuter = context.getService(TaskExecuter.class);
            taskExecuter.execute(task, state, ctx);
            localTaskNode.getPostAction().execute(task);
            return true;
        } else {
            return false;
        }
    }

    private void detectMissingDependencies(LocalTaskNode node) {
        RelatedLocations consumedDirectories = consumedAndProducedLocations.getConsumedDirectories();
        for (String outputPath : node.getMutationInfo().outputPaths) {
            consumedDirectories.getNodesRelatedTo(outputPath).stream()
                .filter(consumerNode -> missesDependency(node, consumerNode))
                .forEach(consumerWithoutDependency -> emitMissingDependencyDeprecationWarning(node, consumerWithoutDependency));
        }
        Set<String> consumedLocations = new LinkedHashSet<>();
        node.getTaskProperties().getInputFileProperties()
            .forEach(spec -> spec.getPropertyFiles().visitStructure(new FileCollectionStructureVisitor() {
                @Override
                public void visitCollection(FileCollectionInternal.Source source, Iterable<File> contents) {
                    contents.forEach(location -> consumedLocations.add(location.getAbsolutePath()));
                }

                @Override
                public void visitGenericFileTree(FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    fileTree.forEach(location -> consumedLocations.add(location.getAbsolutePath()));
                }

                @Override
                public void visitFileTree(File root, PatternSet patterns, FileTreeInternal fileTree) {
                    consumedLocations.add(root.getAbsolutePath());
                }

                @Override
                public void visitFileTreeBackedByFile(File file, FileTreeInternal fileTree, FileSystemMirroringFileTree sourceTree) {
                    consumedLocations.add(file.getAbsolutePath());
                }
            }));
        consumedDirectories.recordRelatedToNode(node, consumedLocations);
        for (String consumedLocation : consumedLocations) {
            consumedAndProducedLocations.getProducedDirectories().getNodesRelatedTo(consumedLocation).stream()
                .filter(producerNode -> missesDependency(producerNode, node))
                .forEach(producerWithoutDependency -> emitMissingDependencyDeprecationWarning(producerWithoutDependency, node));
        }
    }

    private static boolean missesDependency(Node producer, Node consumer) {
        if (consumer == producer) {
            return false;
        }
        if (consumer.getDependencySuccessors().contains(producer)) {
            return false;
        }
        for (Node dependency : consumer.getAllSuccessors()) {
            if (dependency == producer || dependency.getDependencySuccessors().contains(producer)) {
                return false;
            }
        }
        // Do a deep search
        ArrayDeque<Node> queue = new ArrayDeque<>();
        consumer.getAllSuccessors().forEach(queue::add);
        while (!queue.isEmpty()) {
            Node dependency = queue.removeFirst();
            if (dependency == producer) {
                return false;
            }
            dependency.getAllSuccessors().forEach(queue::add);
        }
        return true;
    }

    private void emitMissingDependencyDeprecationWarning(Node producer, Node consumer) {
        DeprecationLogger.deprecateBehaviour(String.format("%s consumes the output of %s, but does not declare a dependency.", consumer, producer)).willBeRemovedInGradle7().undocumented().nagUser();
    }
}
