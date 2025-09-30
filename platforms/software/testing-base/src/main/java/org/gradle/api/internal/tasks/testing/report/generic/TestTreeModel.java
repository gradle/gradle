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
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializedMetadata;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.util.Path;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The model for the test report. Each root is merged into a single tree, but each result is preserved under its root's name, so no merging takes place aside from by name.
 */
public class TestTreeModel {

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
        TestTreeModel rootModel = modelsByPath.get(Path.ROOT);
        if (rootModel == null) {
            throw new IllegalStateException("All provided stores were empty");
        }
        return rootModel;
    }

    private static final class StoreLoader implements Consumer<SerializableTestResultStore.OutputTrackedResult> {

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
        public void accept(SerializableTestResultStore.OutputTrackedResult result) {
            List<Child> children = childrenByParentId.get(result.getOutputId());
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
            List<String> childNames = children.stream().map(child -> child.info.getResult().getName()).collect(Collectors.toList());
            PerRootInfo thisInfo = new PerRootInfo(result, childNames, totalLeafCount, failedLeafCount, skippedLeafCount);
            OptionalLong parentOutputId = result.getParentOutputId();
            if (!parentOutputId.isPresent()) {
                // We have the root, so now we can resolve all paths and attach to the models.
                finalizePath(Path.ROOT, result.getOutputId(), thisInfo);
            } else {
                childrenByParentId.put(parentOutputId.getAsLong(), new Child(result.getOutputId(), thisInfo));
            }
        }

        private void finalizePath(Path path, long id, PerRootInfo rootInfo) {
            // We use LinkedHashMap for the roots to keep them in the order of declaration in TestReport.
            // We use LinkedHashMap for the children to keep them in the order of results in the store.
            TestTreeModel model = modelsByPath.computeIfAbsent(path, p -> new TestTreeModel(p, new LinkedHashMap<>(), new LinkedHashMap<>()));

            if (model.perRootInfo.containsKey(rootIndex)) {
                // Only merge non-leaf nodes.  Leaf nodes might be repeated by test retries, so we'll want to add them all to the model.
                // The merging is necessary to support test engines like TestNG which can split test methods in a single class between
                // multiple test workers.  These results must be recombined in the model to get the correct counts and report structure.
                boolean isLeaf = rootInfo.children.isEmpty();
                if (isLeaf) {
                    model.perRootInfo.put(rootIndex, rootInfo);
                } else {
                    model.perRootInfo.get(rootIndex).merge(rootInfo);
                }
            } else {
                model.perRootInfo.put(rootIndex, rootInfo);
            }

            for (Child child : childrenByParentId.get(id)) {
                Path childPath = path.child(child.info.outputTrackedResult.getInnerResult().getName());
                finalizePath(childPath, child.id, child.info);
                model.children.computeIfAbsent(child.info.outputTrackedResult.getInnerResult().getName(), n -> modelsByPath.get(childPath));
            }
        }
    }

    public static final class PerRootInfo {
        private final SerializableTestResultStore.OutputTrackedResult outputTrackedResult;
        private final List<String> children;
        private int totalLeafCount;
        private int failedLeafCount;
        private int skippedLeafCount;

        public PerRootInfo(SerializableTestResultStore.OutputTrackedResult outputTrackedResult, List<String> children, int totalLeafCount, int failedLeafCount, int skippedLeafCount) {
            this.outputTrackedResult = outputTrackedResult;
            this.children = new ArrayList<>(children);
            this.totalLeafCount = totalLeafCount;
            this.failedLeafCount = failedLeafCount;
            this.skippedLeafCount = skippedLeafCount;
        }

        public SerializableTestResult getResult() {
            return outputTrackedResult.getInnerResult();
        }

        public long getOutputId() {
            return outputTrackedResult.getOutputId();
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
            children.addAll(rootInfo.children);
            totalLeafCount += rootInfo.totalLeafCount;
            failedLeafCount += rootInfo.failedLeafCount;
            skippedLeafCount += rootInfo.skippedLeafCount;
        }
    }

    private final Path path;
    private final Map<Integer, PerRootInfo> perRootInfo;
    private final Map<String, TestTreeModel> children;

    public TestTreeModel(Path path, Map<Integer, PerRootInfo> perRootInfo, Map<String, TestTreeModel> children) {
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
        return path;
    }

    /**
     * Map from root index to the result for this node of the tree in that root.
     *
     * <p>
     * This is not a {@link List} because there are no guarantees that there are results for all roots, i.e. this is a sparse list.
     * </p>
     *
     * @return the results for this node of the tree
     */
    public Map<Integer, PerRootInfo> getPerRootInfo() {
        return Collections.unmodifiableMap(perRootInfo);
    }

    public Map<String, TestTreeModel> getChildren() {
        return Collections.unmodifiableMap(children);
    }

    public Iterable<TestTreeModel> getChildrenOf(int rootIndex) {
        return Iterables.transform(perRootInfo.get(rootIndex).getChildren(), children::get);
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
