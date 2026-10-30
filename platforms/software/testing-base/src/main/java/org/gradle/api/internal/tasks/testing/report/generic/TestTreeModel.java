/*
 * Copyright 2011 the original author or authors.
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

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputEntry;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputRanges;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The model for the test report. Each root is merged into a single tree, but each result is preserved under its root's name, so no merging takes place aside from by name.
 */
public class TestTreeModel {

    private static final TestTreeModel EMPTY_MODEL = new TestTreeModel(SmallPath.ROOT, ImmutableList.of(), ImmutableList.of());

    /**
     * The name of the synthetic child that carries the failure of a childless run merged into a container.
     * This matches the name used by the JUnit Platform adapter for container failures.
     */
    private static final String EXECUTION_ERROR_NAME = "executionError";

    /**
     * Variant of {@link Path} optimized for minimal memory usage.
     * It is constructed strictly in a tree-like fashion, to allow sharing memory between parents and children,
     * and also allowing the segment string to be shared with other code, like the test result itself.
     */
    private static final class SmallPath {
        public static final SmallPath ROOT = new SmallPath(null, "");

        @Nullable
        private final SmallPath parent;
        private final String segment;

        private SmallPath(@Nullable SmallPath parent, String segment) {
            this.parent = parent;
            this.segment = segment;
        }

        public SmallPath child(String segment) {
            return new SmallPath(this, segment);
        }

        public Path toPath() {
            if (parent == null) {
                return Path.ROOT;
            } else {
                return parent.toPath().child(segment);
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof SmallPath)) {
                return false;
            }
            SmallPath other = (SmallPath) obj;
            if (!segment.equals(other.segment)) {
                return false;
            }
            if (parent == null) {
                return other.parent == null;
            } else {
                return parent.equals(other.parent);
            }
        }

        @Override
        public int hashCode() {
            return Objects.hash(segment, parent);
        }
    }

    /**
     * Load and merge a list of test result stores into a single tree model.
     *
     * @param stores the stores to load the models from
     * @return the merged tree model
     */
    public static TestTreeModel loadModelFromStores(List<SerializableTestResultStore> stores) throws Exception {
        Map<SmallPath, TestTreeModel.Builder> modelsByPath = new HashMap<>();
        int rootCount = stores.size();
        for (int i = 0; i < rootCount; i++) {
            SerializableTestResultStore store = stores.get(i);
            store.forEachResult(new StoreLoader(rootCount, i, modelsByPath));
        }
        TestTreeModel.Builder rootBuilder = modelsByPath.get(SmallPath.ROOT);
        if (rootBuilder == null) {
            return EMPTY_MODEL;
        }
        return rootBuilder.build();
    }

    private static final class StoreLoader implements SerializableTestResultStore.ResultProcessor {

        private static final class Child {
            private final long id;
            private final PerRootInfo.Builder info;

            private Child(long id, PerRootInfo.Builder info) {
                this.id = id;
                this.info = info;
            }
        }

        private final int rootCount;
        private final int rootIndex;
        private final Map<SmallPath, TestTreeModel.Builder> modelsByPath;
        private final ListMultimap<Long, Child> childrenByParentId;

        public StoreLoader(int rootCount, int rootIndex, Map<SmallPath, TestTreeModel.Builder> modelsByPath) {
            this.rootCount = rootCount;
            this.rootIndex = rootIndex;
            this.modelsByPath = modelsByPath;
            this.childrenByParentId = ArrayListMultimap.create();
        }

        @Override
        public void process(long id, @Nullable Long parentId, SerializableTestResult result, OutputRanges outputRanges)
            throws IOException {
            boolean isLeaf = childrenByParentId.get(id).isEmpty();
            PerRootInfo.Builder thisInfo = new PerRootInfo.Builder(id, result, outputRanges, isLeaf);
            if (parentId == null) {
                // We have the root, so now we can resolve all paths and attach to the models.
                finalizePath(SmallPath.ROOT, id, thisInfo);
            } else {
                childrenByParentId.put(parentId, new Child(id, thisInfo));
            }
        }

        private void finalizePath(SmallPath path, long id, PerRootInfo.Builder rootInfo) {
            // We use LinkedHashMap for the roots to keep them in the order of declaration in TestReport.
            // We use LinkedHashMap for the children to keep them in the order of results in the store.
            TestTreeModel.Builder model = modelsByPath.computeIfAbsent(path, p -> new TestTreeModel.Builder(rootCount, p));

            model.perRootInfoBuilders.get(rootIndex).add(rootInfo);

            List<Child> children = childrenByParentId.get(id);

            for (Child child : children) {
                String name = child.info.getName();
                SmallPath childPath = path.child(name);
                finalizePath(childPath, child.id, child.info);
                model.children.computeIfAbsent(name, n -> modelsByPath.get(childPath));
            }
        }
    }

    private static final class Builder {
        private final SmallPath path;
        private final List<List<PerRootInfo.Builder>> perRootInfoBuilders;
        final Map<String, TestTreeModel.Builder> children = new LinkedHashMap<>();

        private Builder(int rootCount, SmallPath path) {
            this.perRootInfoBuilders = new ArrayList<>(rootCount);
            for (int i = 0; i < rootCount; i++) {
                perRootInfoBuilders.add(new ArrayList<>());
            }
            this.path = path;
        }

        TestTreeModel build() {
            // Merge first, in case an executionError child is needed, then children can be built.
            List<List<PerRootInfo.Builder>> mergedPerRootInfoBuilders = new ArrayList<>(perRootInfoBuilders.size());
            for (int i = 0; i < perRootInfoBuilders.size(); i++) {
                // Clean up per root info builders as we build, to let GC reclaim their memory.
                List<PerRootInfo.Builder> builders = perRootInfoBuilders.set(i, ImmutableList.of());
                mergedPerRootInfoBuilders.add(mergeRunsIfNeeded(i, builders));
            }
            List<TestTreeModel> builtChildren = buildChildren();
            return new TestTreeModel(path, buildPerRootInfos(mergedPerRootInfoBuilders, builtChildren), builtChildren);
        }

        private static List<List<PerRootInfo>> buildPerRootInfos(
            List<List<PerRootInfo.Builder>> mergedPerRootInfoBuilders,
            List<TestTreeModel> builtChildren
        ) {
            ImmutableList.Builder<List<PerRootInfo>> perRootInfosBuilder =
                ImmutableList.builderWithExpectedSize(mergedPerRootInfoBuilders.size());
            for (int i = 0; i < mergedPerRootInfoBuilders.size(); i++) {
                int rootIndex = i;
                Iterable<PerRootInfo> childInfos = Iterables.concat(
                    Iterables.transform(builtChildren, child -> child.perRootInfo.get(rootIndex))
                );
                List<PerRootInfo.Builder> builders = mergedPerRootInfoBuilders.get(i);
                ImmutableList.Builder<PerRootInfo> infosBuilder = ImmutableList.builderWithExpectedSize(builders.size());
                for (PerRootInfo.Builder builder : builders) {
                    infosBuilder.add(builder.build(childInfos));
                }
                perRootInfosBuilder.add(infosBuilder.build());
            }
            return perRootInfosBuilder.build();
        }

        /**
         * If any run of this node has children, the node is a container, so all of its runs are merged into a single
         * entry, ordered by start time. This recombines test engines like TestNG which can split the test methods of a
         * single class between multiple test workers, and runs of a container that happened to report no children,
         * e.g. a class rescheduled onto a worker that was lost before any test method could report.
         * Otherwise, every run is a leaf, and they might be retries, so they are kept as separate entries.
         * <p>
         * A merged childless run that failed adds an {@value #EXECUTION_ERROR_NAME} child carrying its failures, so
         * the failure is counted, like the JUnit Platform adapter does for container failures. Other merged childless
         * runs add nothing, as the counts of a container are derived only from its children.
         */
        private List<PerRootInfo.Builder> mergeRunsIfNeeded(int rootIndex, List<PerRootInfo.Builder> builders) {
            if (builders.size() <= 1 || builders.stream().allMatch(PerRootInfo.Builder::isLeaf)) {
                return builders;
            }
            // The list is discarded after building, so it's fine to sort it in place.
            builders.sort(PerRootInfo.Builder.BY_START_TIME);
            for (PerRootInfo.Builder builder : builders) {
                if (builder.isLeaf() && builder.getFirstResult().getResultType() == TestResult.ResultType.FAILURE) {
                    children.computeIfAbsent(EXECUTION_ERROR_NAME, n -> new Builder(perRootInfoBuilders.size(), path.child(n)))
                        .perRootInfoBuilders.get(rootIndex)
                        .add(executionError(builder));
                }
            }
            Iterator<PerRootInfo.Builder> iterator = builders.iterator();
            PerRootInfo.Builder first = iterator.next();
            iterator.forEachRemaining(first::merge);
            return Collections.singletonList(first);
        }

        /**
         * Creates the {@value #EXECUTION_ERROR_NAME} leaf for a failed childless run of a container, which reports the
         * run's failures as a test, so they're counted. It takes the run's class name and times, but not its output,
         * which is still shown on the container.
         * <p>
         * Its id is {@link Long#MAX_VALUE} minus the id of the run. Ids in a store count up from 1, so it's nearly
         * impossible that it matches any other id given the number of tests it would take to reach the midpoint.
         * <p>
         * Ideally in the future we'll report container failures properly in the reports, and we can remove this as
         * well as the JUnit Platform adapter's instances of this pattern.
         */
        private static PerRootInfo.Builder executionError(PerRootInfo.Builder failedRun) {
            SerializableTestResult runResult = failedRun.getFirstResult();
            SerializableTestResult.Builder result = SerializableTestResult.builder()
                .name(EXECUTION_ERROR_NAME)
                .displayName(EXECUTION_ERROR_NAME)
                .className(runResult.getClassName())
                .resultType(TestResult.ResultType.FAILURE)
                .startTime(runResult.getStartTime())
                .endTime(runResult.getEndTime());
            runResult.getFailures().forEach(result::addFailure);
            return new PerRootInfo.Builder(
                Long.MAX_VALUE - failedRun.getId(), result.build(), OutputRanges.none(), true
            );
        }

        private List<TestTreeModel> buildChildren() {
            int size = children.size();
            ImmutableList.Builder<TestTreeModel> childrenBuilder = ImmutableList.builderWithExpectedSize(size);
            // Clean up child map as we build, to let GC reclaim their memory.
            for (
                Iterator<TestTreeModel.Builder> iterator = children.values().iterator();
                iterator.hasNext();
            ) {
                Builder value = iterator.next();
                iterator.remove();
                childrenBuilder.add(value.build());
            }
            return childrenBuilder.build();
        }
    }

    private final SmallPath path;
    private final List<List<PerRootInfo>> perRootInfo;
    private final List<TestTreeModel> children;

    private TestTreeModel(
        SmallPath path,
        List<List<PerRootInfo>> perRootInfo,
        List<TestTreeModel> children
    ) {
        this.path = path;
        this.perRootInfo = perRootInfo;
        this.children = children;
    }

    /**
     * The path of this node in the tree.
     *
     * @return the path of this node
     */
    public Path getPath() {
        return path.toPath();
    }

    /**
     * "Sparse" list of per-root info lists, where the index in the outer list is the root index.
     * Missing entries are represented as empty lists.
     */
    public List<List<PerRootInfo>> getPerRootInfo() {
        return perRootInfo;
    }

    public List<TestTreeModel> getChildren() {
        return children;
    }

    /**
     * Returns true if this node is a leaf and has an assumption failure.
     * Assumption failures are recorded when a test is skipped due to an assumption.
     *
     * @return true if this is a leaf with an assumption failure
     */
    public boolean hasAssumptionFailure() {
        for (List<PerRootInfo> infos : getPerRootInfo()) {
            for (PerRootInfo info : infos) {
                for (SerializableTestResult result : info.getResults()) {
                    if (result.getAssumptionFailure() != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if this node is a leaf and all its results are successful
     * (not failed and not skipped).
     *
     * @return true if this is a successful leaf node
     */
    public boolean isSuccessfulLeaf() {
        for (List<PerRootInfo> infos : getPerRootInfo()) {
            for (PerRootInfo info : infos) {
                // A successful leaf has no failures and no skipped tests
                if (info.getFailedLeafCount() > 0 || info.getSkippedLeafCount() > 0) {
                    return false;
                }
            }
        }
        return !perRootInfo.isEmpty();
    }

    /**
     * Determines if this leaf test needs has useful details.
     * <p>
     * This checks if the test is:
     * - A failed test (always useful)
     * - A skipped test with an assumption failure
     * - A successful test is useful if it has output or metadata
     *
     * @return true if this leaf has useful details
     */
    public boolean hasUsefulDetails() {
        if (!children.isEmpty()) {
            // Non-leaves are always useful
            return true;
        }

        // Failed tests are always useful
        if (!isSuccessfulLeaf()) {
            return true;
        }

        // Tests with an assumption failure are useful
        if (hasAssumptionFailure()) {
            return true;
        }

        // Tests with metadata are useful
        if (hasMetadata()) {
            return true;
        }

        // Tests with output are useful
        return hasOutput();
    }

    /**
     * @return true if this test has any output (standard output or standard error)
     */
    private boolean hasOutput() {
        for (List<PerRootInfo> infos : getPerRootInfo()) {
            for (PerRootInfo info : infos) {
                for (OutputEntry outputEntry : info.getOutputEntries()) {
                    if (outputEntry.getOutputRanges().hasOutput()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns true if this leaf node has metadata.
     *
     * @return true if the leaf has metadata
     */
    private boolean hasMetadata() {
        for (List<PerRootInfo> infos : getPerRootInfo()) {
            for (PerRootInfo info : infos) {
                if (!Iterables.isEmpty(info.getMetadatas())) {
                    return true;
                }
            }
        }
        return false;
    }

    public Iterable<TestTreeModel> getChildrenOf(int rootIndex) {
        // Filter children to only those that have results for this root.
        // A child belongs to a root if it has a non-empty perRootInfo list for that root index.
        return Iterables.filter(children, c -> !c.perRootInfo.get(rootIndex).isEmpty());
    }

    /**
     * Returns the maximum number of levels of children in this tree.
     *
     * @return the depth of the tree, where 1 is the root level
     */
    public int getDepth() {
        int deepest = 0;
        for (TestTreeModel treeModel : children) {
            int depth = treeModel.getDepth();
            if (depth > deepest) {
                deepest = depth;
            }
        }
        return deepest + 1;
    }

    /**
     * Walks the tree depth-first, calling the given consumer for each node.
     *
     * @param consumer the consumer to call for each node
     */
    public void walkDepthFirst(Consumer<TestTreeModel> consumer) {
        consumer.accept(this);
        for (TestTreeModel child : children) {
            child.walkDepthFirst(consumer);
        }
    }

    private static final int INDENT_SIZE = 2;

    /**
     * Dumps the basic tree structure to an appendable, for debugging purposes.
     *
     * @param appendable the appendable to dump to
     */
    // This may be used for debugging, so keep it around even when not used.
    @SuppressWarnings("unused")
    public void dumpStructure(Appendable appendable) {
        dumpStructure(appendable, 0);
    }

    private void dumpStructure(Appendable appendable, int indent) {
        try {
            for (int i = 0; i < indent; i++) {
                appendable.append(' ');
            }
            Path path = getPath();
            String name = path.segmentCount() == 0 ? ":" : path.getName();
            appendable.append("- ").append(name).append('\n');
            for (TestTreeModel child : children) {
                child.dumpStructure(appendable, indent + INDENT_SIZE);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to dump test tree structure", e);
        }
    }
}
