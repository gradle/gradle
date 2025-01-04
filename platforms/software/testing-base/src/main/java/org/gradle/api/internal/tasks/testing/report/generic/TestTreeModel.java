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
            TestTreeModel model = modelsByPath.computeIfAbsent(path, p -> new TestTreeModel(p, new LinkedHashMap<>(), new HashMap<>()));
            model.perRootInfo.put(rootIndex, rootInfo);
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
        private final int totalLeafCount;
        private final int failedLeafCount;
        private final int skippedLeafCount;

        public PerRootInfo(SerializableTestResultStore.OutputTrackedResult outputTrackedResult, List<String> children, int totalLeafCount, int failedLeafCount, int skippedLeafCount) {
            this.outputTrackedResult = outputTrackedResult;
            this.children = children;
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
}
