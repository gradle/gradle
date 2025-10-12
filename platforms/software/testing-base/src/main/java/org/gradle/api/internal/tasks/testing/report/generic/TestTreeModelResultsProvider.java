/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report.generic;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.google.common.io.CharStreams;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.UncheckedException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class TestTreeModelResultsProvider implements TestResultsProvider {
    private static final class ClassNode {
        final TestClassResult result;
        /**
         * All output IDs from this "class" and the intermediate nodes. Does not include output IDs from "method"s.
         */
        final ImmutableSet<Long> outputIds;
        final ImmutableSet<Long> methodOutputIds;

        ClassNode(TestClassResult result, ImmutableSet<Long> outputIds, ImmutableSet<Long> methodOutputIds) {
            this.result = result;
            this.outputIds = outputIds;
            this.methodOutputIds = methodOutputIds;
        }
    }

    private static Map<Long, ClassNode> createClasses(TestTreeModel root) {
        ListMultimap<TestTreeModel, TestTreeModel> leavesByGroupingNode = LinkedListMultimap.create();
        walkLeaves(root, leaf -> {
            TestTreeModel owningNode = findGroupingNode(leaf);
            leavesByGroupingNode.put(owningNode, leaf);
        });

        ImmutableMap.Builder<Long, ClassNode> classesById = ImmutableMap.builderWithExpectedSize(
            leavesByGroupingNode.keySet().size()
        );
        long nextClassId = 1;
        for (Map.Entry<TestTreeModel, List<TestTreeModel>> entry : Multimaps.asMap(leavesByGroupingNode).entrySet()) {
            TestTreeModel groupingNode = entry.getKey();
            List<TestTreeModel> leaves = entry.getValue();

            ImmutableSet.Builder<Long> methodOutputIds = ImmutableSet.builder();
            TestClassResult classResult = buildClassResult(groupingNode, leaves, nextClassId, methodOutputIds);
            nextClassId++;

            ImmutableSet.Builder<Long> outputIds = ImmutableSet.builder();
            groupingNode.walkDepthFirst(node -> {
                if (node.getChildren().isEmpty()) {
                    // One of our leaves, skip
                    return;
                }
                for (TestTreeModel.PerRootInfo perRootInfo : node.getPerRootInfo().get(0)) {
                    outputIds.add(perRootInfo.getOutputId());
                }
            });

            classesById.put(classResult.getId(), new ClassNode(classResult, outputIds.build(), methodOutputIds.build()));
        }
        return classesById.build();
    }

    private static TestClassResult buildClassResult(
        TestTreeModel groupingNode,
        List<TestTreeModel> leaves,
        long nextClassId,
        ImmutableSet.Builder<Long> methodOutputIds
    ) {
        TestClassResult classResult = createEmptyClassResult(groupingNode, nextClassId);

        for (TestTreeModel leaf : leaves) {
            for (TestTreeModel.PerRootInfo leafPerRootInfo : leaf.getPerRootInfo().get(0)) {
                classResult.add(buildMethodResult(leafPerRootInfo));
                methodOutputIds.add(leafPerRootInfo.getOutputId());
            }
        }
        return classResult;
    }

    private static TestClassResult createEmptyClassResult(TestTreeModel groupingNode, long nextClassId) {
        List<TestTreeModel.PerRootInfo> perRootInfos = groupingNode.getPerRootInfo().get(0);
        if (perRootInfos.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one run for grouping node " + groupingNode.getPath() +
                    " but found: " + perRootInfos.size()
            );
        }
        TestTreeModel.PerRootInfo perRootInfo = perRootInfos.get(0);
        return new TestClassResult(
            nextClassId,
            perRootInfo.getResult().getName(),
            perRootInfo.getResult().getDisplayName(),
            perRootInfo.getResult().getStartTime()
        );
    }

    private static TestMethodResult buildMethodResult(TestTreeModel.PerRootInfo perRootInfo) {
        SerializableTestResult result = perRootInfo.getResult();
        TestMethodResult methodResult = new TestMethodResult(
            perRootInfo.getOutputId(),
            result.getName(),
            result.getDisplayName(),
            result.getResultType(),
            result.getDuration(),
            result.getEndTime()
        );
        methodResult.getFailures().addAll(result.getFailures());
        if (result.getAssumptionFailure() != null) {
            SerializableFailure assumptionFailure = result.getAssumptionFailure();
            methodResult.setAssumptionFailure(
                assumptionFailure.getMessage(), assumptionFailure.getStackTrace(), assumptionFailure.getExceptionType()
            );
        }
        return methodResult;
    }

    private static TestTreeModel findGroupingNode(TestTreeModel leaf) {
        String className = getOnlyClassName(leaf);
        return findGroupingNode(leaf, className);
    }

    private static TestTreeModel findGroupingNode(TestTreeModel leaf, @Nullable String className) {
        TestTreeModel current = leaf;
        TestTreeModel parent;
        while ((parent = current.getParent()) != null) {
            if (className != null && className.equals(parent.getPath().getName())) {
                return parent;
            }
            // Pick highest non-root node if no class name match
            if (parent.getParent() == null) {
                // Parent is the root, so the current is the highest non-root node
                return current;
            }
            current = parent;
        }
        // Reached the root, return it
        return current;
    }

    @Nullable
    private static String getOnlyClassName(TestTreeModel leaf) {
        String className = null;
        for (TestTreeModel.PerRootInfo perRootInfo : leaf.getPerRootInfo().get(0)) {
            String runClassName = perRootInfo.getResult().getClassName();
            if (runClassName != null) {
                if (className == null) {
                    className = runClassName;
                } else if (!className.equals(runClassName)) {
                    throw new IllegalStateException("Runs at " + leaf.getPath() + " have different class names: " + className + " and " + runClassName);
                }
            }
        }
        return className;
    }

    private static void walkLeaves(
        TestTreeModel base,
        Consumer<TestTreeModel> leafConsumer
    ) {
        base.walkDepthFirst(node -> {
            // Ignore the root node as a leaf, it is not a test
            if (node.getChildren().isEmpty() && node.getParent() != null) {
                leafConsumer.accept(node);
            }
        });
    }

    private final Map<Long, ClassNode> classesById;
    private final SerializableTestResultStore.OutputReader outputReader;

    public TestTreeModelResultsProvider(TestTreeModel root, SerializableTestResultStore.OutputReader outputReader) {
        this.classesById = createClasses(root);
        this.outputReader = outputReader;
    }

    @Override
    public void visitClasses(Action<? super TestClassResult> visitor) {
        for (ClassNode value : classesById.values()) {
            visitor.execute(value.result);
        }
    }

    @Override
    public void writeAllOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        ClassNode classNode = classesById.get(classId);
        if (classNode == null) {
            throw new IllegalArgumentException("No class with id " + classId);
        }
        for (long outputId : classNode.outputIds) {
            copyOutput(outputId, destination, writer);
        }
        for (long outputId : classNode.methodOutputIds) {
            copyOutput(outputId, destination, writer);
        }
    }

    @Override
    public void writeNonTestOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        ClassNode classNode = classesById.get(classId);
        if (classNode == null) {
            throw new IllegalArgumentException("No class with id " + classId);
        }
        for (long outputId : classNode.outputIds) {
            copyOutput(outputId, destination, writer);
        }
    }

    @Override
    public void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
        ClassNode classNode = classesById.get(classId);
        if (classNode == null) {
            throw new IllegalArgumentException("No class with id " + classId);
        }
        if (!classNode.methodOutputIds.contains(testId)) {
            throw new IllegalArgumentException("No test with id " + testId + " in class with id " + classId);
        }
        copyOutput(testId, destination, writer);
    }

    private void copyOutput(long outputId, TestOutputEvent.Destination destination, Writer writer) {
        try (Reader output = outputReader.getOutput(outputId, destination)) {
            CharStreams.copy(output, writer);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public boolean hasOutput(long classId, TestOutputEvent.Destination destination) {
        ClassNode model = classesById.get(classId);
        if (model != null) {
            for (long outputId : Iterables.concat(model.outputIds, model.methodOutputIds)) {
                if (outputReader.hasOutput(outputId, destination)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasOutput(long classId, long testId, TestOutputEvent.Destination destination) {
        ClassNode model = classesById.get(classId);
        if (model != null && model.methodOutputIds.contains(testId)) {
            return outputReader.hasOutput(testId, destination);
        }
        return false;
    }

    @Override
    public boolean isHasResults() {
        return !classesById.isEmpty();
    }

    @Override
    public void close() throws IOException {
        outputReader.close();
    }
}
