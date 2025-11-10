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
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputEntry;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputTrackedResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializedMetadata;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The model for the test report. Each root is merged into a single tree, but each result is preserved under its root's name, so no merging takes place aside from by name.
 */
public class TestTreeModel {

    private static final TestTreeModel EMPTY_MODEL = new TestTreeModel(null, Path.ROOT, ImmutableListMultimap.of(), ImmutableMap.of());

    /**
     * Load and merge a list of test result stores into a single tree model.
     *
     * @param stores the stores to load the models from
     * @return the merged tree model
     */
    public static TestTreeModel loadModelFromStores(List<SerializableTestResultStore> stores) throws IOException {
        Map<Path, TestTreeModel> modelsByPath = new HashMap<>();
        for (int i = 0; i < stores.size(); i++) {
            SerializableTestResultStore store = stores.get(i);
            store.forEachResult(new StoreLoader(i, modelsByPath));
        }
        return modelsByPath.getOrDefault(Path.ROOT, EMPTY_MODEL);
    }

    private static final class StoreLoader implements Consumer<OutputTrackedResult> {

        private static final class Child {
            private final long id;
            private final PerRootInfo info;

            private Child(long id, PerRootInfo info) {
                this.id = id;
                this.info = info;
            }
        }

        private final int rootIndex;
        private final Map<Path, TestTreeModel> modelsByPath;
        private final ListMultimap<Long, Child> childrenByParentId;

        public StoreLoader(int rootIndex, Map<Path, TestTreeModel> modelsByPath) {
            this.rootIndex = rootIndex;
            this.modelsByPath = modelsByPath;
            this.childrenByParentId = ArrayListMultimap.create();
        }

        @Override
        public void accept(OutputTrackedResult result) {
            List<Child> children = childrenByParentId.get(result.getId());
            int totalLeafCount = 0;
            int failedLeafCount = 0;
            int skippedLeafCount = 0;
            for (Child child : children) {
                totalLeafCount += child.info.totalLeafCount;
                failedLeafCount += child.info.failedLeafCount;
                skippedLeafCount += child.info.skippedLeafCount;
            }
            if (children.isEmpty()) {
                // This is a leaf, so compute the counts for itself.
                totalLeafCount = 1;
                if (result.getInnerResult().getResultType() == TestResult.ResultType.FAILURE) {
                    failedLeafCount = 1;
                } else if (result.getInnerResult().getResultType() == TestResult.ResultType.SKIPPED) {
                    skippedLeafCount = 1;
                }
            }
            List<String> childNames = new ArrayList<>(children.size());
            BitSet childIsLeaf = new BitSet(children.size());
            for (int i = 0; i < children.size(); i++) {
                Child child = children.get(i);
                String name = child.info.getResult().getName();
                childNames.add(name);
                if (child.info.children.isEmpty()) {
                    childIsLeaf.set(i);
                }
            }
            PerRootInfo thisInfo = new PerRootInfo(result, childNames, childIsLeaf, totalLeafCount, failedLeafCount, skippedLeafCount);
            OptionalLong parentOutputId = result.getParentId();
            if (!parentOutputId.isPresent()) {
                // We have the root, so now we can resolve all paths and attach to the models.
                finalizePath(null, Path.ROOT, result.getId(), thisInfo);
            } else {
                childrenByParentId.put(parentOutputId.getAsLong(), new Child(result.getId(), thisInfo));
            }
        }

        private void finalizePath(@Nullable TestTreeModel parent, Path path, long id, PerRootInfo rootInfo) {
            // We use LinkedHashMap for the roots to keep them in the order of declaration in TestReport.
            // We use LinkedHashMap for the children to keep them in the order of results in the store.
            TestTreeModel model = modelsByPath.computeIfAbsent(path, p -> new TestTreeModel(parent, p, LinkedListMultimap.create(), new LinkedHashMap<>()));

            List<PerRootInfo> existingRootInfos = model.perRootInfo.get(rootIndex);
            if (!existingRootInfos.isEmpty()) {
                // Only merge non-leaf nodes.  Leaf nodes might be repeated by test retries, so we'll want to add them all to the model.
                // The merging is necessary to support test engines like TestNG which can split test methods in a single class between
                // multiple test workers.  These results must be recombined in the model to get the correct counts and report structure.
                boolean isLeaf = rootInfo.children.isEmpty();
                if (isLeaf) {
                    existingRootInfos.add(rootInfo);
                } else {
                    // Merge into the one that is also not a leaf if possible, otherwise just merge into the first one.
                    PerRootInfo toMerge = existingRootInfos.stream()
                        .filter(info -> !info.children.isEmpty())
                        .findFirst()
                        .orElseGet(() -> existingRootInfos.get(0));
                    toMerge.merge(rootInfo);
                }
            } else {
                existingRootInfos.add(rootInfo);
            }

            List<Child> children = childrenByParentId.get(id);

            for (Child child : children) {
                String name = child.info.getResult().getName();
                Path childPath = path.child(name);
                finalizePath(model, childPath, child.id, child.info);
                model.children.computeIfAbsent(name, n -> modelsByPath.get(childPath));
            }
        }
    }

    public static final class PerRootInfo {
        private OutputTrackedResult outputTrackedResult;
        private final List<String> children;
        private final BitSet childIsLeaf;
        private int totalLeafCount;
        private int failedLeafCount;
        private int skippedLeafCount;

        public PerRootInfo(OutputTrackedResult outputTrackedResult, List<String> children, BitSet childIsLeaf, int totalLeafCount, int failedLeafCount, int skippedLeafCount) {
            this.outputTrackedResult = outputTrackedResult;
            this.children = new ArrayList<>(children);
            this.childIsLeaf = childIsLeaf;
            this.totalLeafCount = totalLeafCount;
            this.failedLeafCount = failedLeafCount;
            this.skippedLeafCount = skippedLeafCount;
        }

        public SerializableTestResult getResult() {
            return outputTrackedResult.getInnerResult();
        }

        public long getId() {
            return outputTrackedResult.getId();
        }

        public OutputEntry getOutputEntry() {
            return outputTrackedResult.getOutputEntry();
        }

        public List<String> getChildren() {
            return Collections.unmodifiableList(children);
        }

        public int getTotalLeafCount() {
            return totalLeafCount;
        }

        public int getFailedLeafCount() {
            return failedLeafCount;
        }

        public int getSkippedLeafCount() {
            return skippedLeafCount;
        }

        public List<SerializedMetadata> getMetadatas() {
            return outputTrackedResult.getInnerResult().getMetadatas();
        }

        public void merge(PerRootInfo rootInfo) {
            Set<String> knownChildren = ImmutableSet.copyOf(this.children);
            List<String> strings = rootInfo.children;
            for (int i = 0; i < strings.size(); i++) {
                String newChild = strings.get(i);
                boolean newChildIsLeaf = rootInfo.childIsLeaf.get(i);
                // If this is a non-leaf child, and it matches an existing non-leaf child, skip adding it.
                if (!newChildIsLeaf && knownChildren.contains(newChild) && isExistingNonLeafChild(newChild)) {
                    continue;
                }
                // Passed all tests, so add the child.
                this.children.add(newChild);
                if (newChildIsLeaf) {
                    this.childIsLeaf.set(this.children.size() - 1);
                }
            }

            totalLeafCount += rootInfo.totalLeafCount;
            failedLeafCount += rootInfo.failedLeafCount;
            skippedLeafCount += rootInfo.skippedLeafCount;

            SerializableTestResult mergedResult = getResult().merge(rootInfo.getResult());
            outputTrackedResult = outputTrackedResult.withInnerResult(mergedResult);
        }

        private boolean isExistingNonLeafChild(String child) {
            boolean anyNonLeaf = false;
            for (int j = 0; j < this.children.size(); j++) {
                if (this.children.get(j).equals(child) && !this.childIsLeaf.get(j)) {
                    // This child has the same name, and is not a leaf, so we can skip adding it.
                    anyNonLeaf = true;
                    break;
                }
            }
            return anyNonLeaf;
        }
    }

    @Nullable
    private final TestTreeModel parent;
    private final Path path;
    private final ListMultimap<Integer, PerRootInfo> perRootInfo;
    private final Map<String, TestTreeModel> children;

    private TestTreeModel(@Nullable TestTreeModel parent, Path path, ListMultimap<Integer, PerRootInfo> perRootInfo, Map<String, TestTreeModel> children) {
        this.parent = parent;
        this.path = path;
        this.perRootInfo = perRootInfo;
        this.children = children;
    }

    @Nullable
    public TestTreeModel getParent() {
        return parent;
    }

    /**
     * The path of this node in the tree.
     *
     * @return the path of this node
     */
    public Path getPath() {
        return path;
    }

    /**
     * Map from root index to the result(s) for this node of the tree in that root.
     *
     * <p>
     * This is not a {@link List} because there are no guarantees that there are results for all roots, i.e. this is a sparse list.
     * </p>
     *
     * @return the results for this node of the tree
     */
    public ListMultimap<Integer, PerRootInfo> getPerRootInfo() {
        return Multimaps.unmodifiableListMultimap(perRootInfo);
    }

    public Map<String, TestTreeModel> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    public Iterable<TestTreeModel> getChildrenOf(int rootIndex) {
        // There should only be one perRootInfo with children.
        PerRootInfo perRootInfoWithChildren = perRootInfo.get(rootIndex).stream()
            .filter(info -> !info.children.isEmpty())
            .findFirst()
            .orElse(null);
        if (perRootInfoWithChildren == null) {
            return Collections.emptyList();
        }
        return Iterables.transform(
            // Take a unique ordered set of the child names, to only return one result per unique child name.
            // Consumers of this should iterate over the getPerRootInfo() to get all results for a given child name.
            ImmutableSet.copyOf(perRootInfoWithChildren.getChildren()),
            children::get
        );
    }

    /**
     * Returns the maximum number of levels of children in this tree.
     *
     * @return the depth of the tree, where 1 is the root level
     */
    public int getDepth() {
        int deepest = 0;
        for (TestTreeModel treeModel : children.values()) {
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
        for (TestTreeModel child : children.values()) {
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
            String name = path.segmentCount() == 0 ? ":" : path.getName();
            appendable.append("- ").append(name).append('\n');
            for (TestTreeModel child : children.values()) {
                child.dumpStructure(appendable, indent + INDENT_SIZE);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to dump test tree structure", e);
        }
    }
}
