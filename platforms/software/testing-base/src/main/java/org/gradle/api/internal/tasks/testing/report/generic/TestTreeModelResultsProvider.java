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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import org.gradle.api.Action;
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestMethodResult;
import org.gradle.api.internal.tasks.testing.junit.result.TestResultsProvider;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputEntry;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputReader;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestOutputEvent;
import org.gradle.internal.UncheckedException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A {@link TestResultsProvider} that provides results from a {@link TestTreeModel}. This handles condensing the multiple
 * levels of the tree model into test classes and test methods, so that the results can be consumed by existing report
 * renderers, such as the JUnit XML report renderer.
 *
 * <p>
 * Any nodes above a test class will not be represented in the results. Primarily, this will be an issue for the root node,
 * but may also affect results from JUnit 4 suites or non-class-based tests.
 * </p>
 */
public final class TestTreeModelResultsProvider implements TestResultsProvider {

    public static void useResultsFrom(Path resultsDir, Consumer<TestTreeModelResultsProvider> resultsConsumer) {
        SerializableTestResultStore resultsStore = new SerializableTestResultStore(resultsDir);
        try  {
            TestTreeModel root = TestTreeModel.loadModelFromStores(Collections.singletonList(resultsStore));
            TestTreeModelResultsProvider resultsProvider = new TestTreeModelResultsProvider(root, resultsStore.createOutputReader());
            resultsConsumer.accept(resultsProvider);
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    private static final class ClassNode {
        final TestClassResult result;
        /**
         * All output entries from this "class" and the intermediate nodes. Does not include output entries from "method"s.
         */
        final ImmutableList<OutputEntry> outputEntries;
        final ImmutableMap<Long, OutputEntry> methodOutputEntries;

        ClassNode(TestClassResult result, ImmutableList<OutputEntry> outputEntries, ImmutableMap<Long, OutputEntry> methodOutputEntries) {
            this.result = result;
            this.outputEntries = outputEntries;
            this.methodOutputEntries = methodOutputEntries;
        }
    }

    private static final Comparator<PerRootInfo> PER_ROOT_INFO_BY_START_TIME =
        Comparator.comparing(leaf -> leaf.getResult().getStartTime());

    private static Map<Long, ClassNode> createClasses(TestTreeModel root) {
        Map<org.gradle.util.Path, TestTreeModel> parentOfPath = buildParentOfPathMap(root);
        ListMultimap<TestTreeModel, PerRootInfo> leavesByGroupingNode = LinkedListMultimap.create();
        walkLeaves(parentOfPath, root, leaf -> {
            for (PerRootInfo perRootInfo : leaf.getPerRootInfo().get(0)) {
                TestTreeModel groupingNode = findGroupingNode(parentOfPath, leaf, perRootInfo.getResult().getClassName());
                leavesByGroupingNode.put(groupingNode, perRootInfo);
            }
        });

        ImmutableMap.Builder<Long, ClassNode> classesById = ImmutableMap.builderWithExpectedSize(
            leavesByGroupingNode.keySet().size()
        );
        long nextClassId = 1;
        for (Map.Entry<TestTreeModel, List<PerRootInfo>> entry : Multimaps.asMap(leavesByGroupingNode).entrySet()) {
            TestTreeModel groupingNode = entry.getKey();
            List<PerRootInfo> leaves = new ArrayList<>(entry.getValue());

            // We want these sorted by start time in order to preserve ordering between runs.
            leaves.sort(PER_ROOT_INFO_BY_START_TIME);

            ImmutableMap.Builder<Long, OutputEntry> methodOutputEntries = ImmutableMap.builder();
            TestClassResult classResult = buildClassResult(groupingNode, leaves, nextClassId, methodOutputEntries);
            nextClassId++;

            ImmutableList.Builder<OutputEntry> outputEntries = ImmutableList.builder();
            groupingNode.walkDepthFirst(node -> {
                if (node.getChildren().isEmpty()) {
                    // One of our leaves, skip
                    return;
                }
                for (PerRootInfo perRootInfo : node.getPerRootInfo().get(0)) {
                    outputEntries.add(perRootInfo.getOutputEntry());
                }
            });

            classesById.put(classResult.getId(), new ClassNode(classResult, outputEntries.build(), methodOutputEntries.build()));
        }
        return classesById.build();
    }

    private static Map<org.gradle.util.Path, TestTreeModel> buildParentOfPathMap(TestTreeModel root) {
        ImmutableMap.Builder<org.gradle.util.Path, TestTreeModel> parentOfPath = ImmutableMap.builder();
        addToParentOfPathMap(root, parentOfPath);
        return parentOfPath.build();
    }

    private static void addToParentOfPathMap(
        TestTreeModel node,
        ImmutableMap.Builder<org.gradle.util.Path, TestTreeModel> parentOfPath
    ) {
        for (TestTreeModel child : node.getChildren().values()) {
            parentOfPath.put(child.getPath(), node);
            addToParentOfPathMap(child, parentOfPath);
        }
    }

    private static TestClassResult buildClassResult(
        TestTreeModel groupingNode,
        List<PerRootInfo> leaves,
        long nextClassId,
        ImmutableMap.Builder<Long, OutputEntry> methodOutputEntries
    ) {
        TestClassResult classResult = createEmptyClassResult(groupingNode, nextClassId);

        for (PerRootInfo leafPerRootInfo : leaves) {
            classResult.add(buildMethodResult(leafPerRootInfo));
            methodOutputEntries.put(leafPerRootInfo.getId(), leafPerRootInfo.getOutputEntry());
        }
        return classResult;
    }

    private static TestClassResult createEmptyClassResult(TestTreeModel groupingNode, long nextClassId) {
        List<PerRootInfo> perRootInfos = groupingNode.getPerRootInfo().get(0);
        if (perRootInfos.size() != 1) {
            throw new IllegalStateException(
                "Expected exactly one run for grouping node " + groupingNode.getPath() +
                    " but found: " + perRootInfos.size()
            );
        }
        PerRootInfo perRootInfo = perRootInfos.get(0);
        return new TestClassResult(
            nextClassId,
            perRootInfo.getResult().getName(),
            perRootInfo.getResult().getDisplayName(),
            perRootInfo.getResult().getStartTime()
        );
    }

    private static TestMethodResult buildMethodResult(PerRootInfo perRootInfo) {
        SerializableTestResult result = perRootInfo.getResult();
        TestMethodResult methodResult = new TestMethodResult(
            perRootInfo.getId(),
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

    private static TestTreeModel findGroupingNode(
        Map<org.gradle.util.Path, TestTreeModel> parentOfPath, TestTreeModel leaf, @Nullable String className
    ) {
        TestTreeModel current = leaf;
        TestTreeModel parent;
        while ((parent = parentOfPath.get(current.getPath())) != null) {
            if (className != null && className.equals(parent.getPath().getName())) {
                return parent;
            }
            // Pick highest non-root node if no class name match
            // But don't group the leaf using itself, that doesn't make sense.
            boolean parentHasParent = parentOfPath.containsKey(parent.getPath());
            if (!parentHasParent && current != leaf) {
                // Parent is the root, so the current is the highest non-root node
                return current;
            }
            current = parent;
        }
        // Reached the root, return it
        return current;
    }

    private static void walkLeaves(
        Map<org.gradle.util.Path, TestTreeModel> parentOfPath,
        TestTreeModel base,
        Consumer<TestTreeModel> leafConsumer
    ) {
        base.walkDepthFirst(node -> {
            if (node.getChildren().isEmpty()) {
                // Ignore the root node as a leaf, it is not a test
                boolean hasParent = parentOfPath.containsKey(node.getPath());
                if (hasParent) {
                    leafConsumer.accept(node);
                }
            }
        });
    }

    private final Map<Long, ClassNode> classesById;
    private final OutputReader outputReader;

    public TestTreeModelResultsProvider(TestTreeModel root, OutputReader outputReader) {
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
        try {
            outputReader.useTestOutputEvents(
                Iterables.concat(classNode.outputEntries, classNode.methodOutputEntries.values()), destination,
                event -> writer.write(event.getMessage())
            );
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void writeNonTestOutput(long classId, TestOutputEvent.Destination destination, Writer writer) {
        ClassNode classNode = classesById.get(classId);
        if (classNode == null) {
            throw new IllegalArgumentException("No class with id " + classId);
        }
        try {
            outputReader.useTestOutputEvents(
                classNode.outputEntries, destination,
                event -> writer.write(event.getMessage())
            );
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public void writeTestOutput(long classId, long testId, TestOutputEvent.Destination destination, Writer writer) {
        ClassNode classNode = classesById.get(classId);
        if (classNode == null) {
            throw new IllegalArgumentException("No class with id " + classId);
        }
        OutputEntry testEntry = classNode.methodOutputEntries.get(testId);
        if (testEntry == null) {
            throw new IllegalArgumentException("No test with id " + testId + " in class with id " + classId);
        }
        try {
            outputReader.useTestOutputEvents(
                testEntry, destination,
                event -> writer.write(event.getMessage())
            );
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public boolean hasOutput(long classId, long testId, TestOutputEvent.Destination destination) {
        ClassNode model = classesById.get(classId);
        if (model != null) {
            OutputEntry entry = model.methodOutputEntries.get(testId);
            if (entry != null) {
                return outputReader.hasOutput(entry, destination);
            }
        }
        return false;
    }

    @Override
    public void close() throws IOException {
        outputReader.close();
    }
}
