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
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.util.Path;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        for (SerializableTestResultStore store : stores) {
            store.forEachResult(new StoreLoader(modelsByPath));
        }
        TestTreeModel rootModel = modelsByPath.get(Path.ROOT);
        if (rootModel == null) {
            throw new IllegalStateException("All provided stores were empty");
        }
        return rootModel;
    }

    private static final class StoreLoader implements Consumer<SerializableTestResultStore.StoredResult> {

        private static final class Child {
            private final Object id;
            private final PerRootInfo info;

            private Child(Object id, PerRootInfo info) {
                this.id = id;
                this.info = info;
            }
        }

        private final Map<Path, TestTreeModel> modelsByPath;
        private final ListMultimap<Object, Child> childrenByParentId;

        public StoreLoader(Map<Path, TestTreeModel> modelsByPath) {
            this.modelsByPath = modelsByPath;
            this.childrenByParentId = ArrayListMultimap.create();
        }

        @Override
        public void accept(SerializableTestResultStore.StoredResult result) {
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
                if (result.getTestResult().getResultType() == TestResult.ResultType.FAILURE) {
                    failedLeafCount = 1;
                } else if (result.getTestResult().getResultType() == TestResult.ResultType.SKIPPED) {
                    skippedLeafCount = 1;
                }
            }
            List<String> childNames = children.stream().map(child -> child.info.result.getTestResult().getName()).collect(Collectors.toList());
            PerRootInfo thisInfo = new PerRootInfo(result, childNames, totalLeafCount, failedLeafCount, skippedLeafCount);
            if (result.getParentId() == null) {
                // We have the root, so now we can resolve all paths and attach to the models.
                finalizePath(result.getTestResult().getName(), Path.ROOT, result.getId(), thisInfo);
            } else {
                childrenByParentId.put(result.getParentId(), new Child(result.getId(), thisInfo));
            }
        }

        private void finalizePath(String rootName, Path path, Object id, PerRootInfo rootInfo) {
            // We use LinkedHashMap for the roots to keep them in the order of declaration in TestReport.
            TestTreeModel model = modelsByPath.computeIfAbsent(path, p -> new TestTreeModel(p, new LinkedHashMap<>(), new HashMap<>()));
            model.perRootInfo.put(rootName, rootInfo);
            for (Child child : childrenByParentId.get(id)) {
                Path childPath = path.child(child.info.result.getTestResult().getName());
                finalizePath(rootName, childPath, child.id, child.info);
                model.children.computeIfAbsent(child.info.result.getTestResult().getName(), n -> modelsByPath.get(childPath));
            }
        }
    }

    public static final class PerRootInfo {
        private final SerializableTestResultStore.StoredResult result;
        private final List<String> children;
        private final int totalLeafCount;
        private final int failedLeafCount;
        private final int skippedLeafCount;

        public PerRootInfo(SerializableTestResultStore.StoredResult result, List<String> children, int totalLeafCount, int failedLeafCount, int skippedLeafCount) {
            this.result = result;
            this.children = children;
            this.totalLeafCount = totalLeafCount;
            this.failedLeafCount = failedLeafCount;
            this.skippedLeafCount = skippedLeafCount;
        }

        public SerializableTestResultStore.StoredResult getResult() {
            return result;
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
    }

    private final Path path;
    private final Map<String, PerRootInfo> perRootInfo;
    private final Map<String, TestTreeModel> children;

    public TestTreeModel(Path path, Map<String, PerRootInfo> perRootInfo, Map<String, TestTreeModel> children) {
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
     * Map from root name to the result for this node of the tree in that root.
     *
     * @return the results for this node of the tree
     */
    public Map<String, PerRootInfo> getPerRootInfo() {
        return Collections.unmodifiableMap(perRootInfo);
    }

    /**
     * Map of children, lookup should be using names from the {@link PerRootInfo#getChildren()}.
     */
    public Map<String, TestTreeModel> getChildren() {
        return children;
    }
}
