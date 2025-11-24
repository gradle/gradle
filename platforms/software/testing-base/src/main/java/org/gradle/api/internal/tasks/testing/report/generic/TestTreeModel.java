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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputRanges;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResultStore;
import org.gradle.api.tasks.testing.TestResult;
import org.gradle.util.Path;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.BitSet;
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
            List<Child> children = childrenByParentId.get(id);
            int totalLeafCount = 0;
            int failedLeafCount = 0;
            int skippedLeafCount = 0;
            for (Child child : children) {
                totalLeafCount += child.info.getTotalLeafCount();
                failedLeafCount += child.info.getFailedLeafCount();
                skippedLeafCount += child.info.getSkippedLeafCount();
            }
            if (children.isEmpty()) {
                // This is a leaf, so compute the counts for itself.
                totalLeafCount = 1;
                if (result.getResultType() == TestResult.ResultType.FAILURE) {
                    failedLeafCount = 1;
                } else if (result.getResultType() == TestResult.ResultType.SKIPPED) {
                    skippedLeafCount = 1;
                }
            }
            List<String> childNames = new ArrayList<>(children.size());
            BitSet childIsLeaf = new BitSet(children.size());
            for (int i = 0; i < children.size(); i++) {
                Child child = children.get(i);
                String name = child.info.getName();
                childNames.add(name);
                if (child.info.isLeaf()) {
                    childIsLeaf.set(i);
                }
            }
            PerRootInfo.Builder thisInfo = new PerRootInfo.Builder(id, result, outputRanges, childNames, childIsLeaf, totalLeafCount, failedLeafCount, skippedLeafCount);
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

            List<PerRootInfo.Builder> existingRootInfos = model.perRootInfoBuilders.get(rootIndex);
            if (!existingRootInfos.isEmpty()) {
                // Only merge non-leaf nodes.  Leaf nodes might be repeated by test retries, so we'll want to add them all to the model.
                // The merging is necessary to support test engines like TestNG which can split test methods in a single class between
                // multiple test workers.  These results must be recombined in the model to get the correct counts and report structure.
                boolean isLeaf = rootInfo.isLeaf();
                if (isLeaf) {
                    existingRootInfos.add(rootInfo);
                } else {
                    // Merge into the one that is also not a leaf if possible, otherwise just merge into the first one.
                    PerRootInfo.Builder toMerge = existingRootInfos.stream()
                        .filter(info -> !info.isLeaf())
                        .findFirst()
                        .orElseGet(() -> existingRootInfos.get(0));
                    toMerge.merge(rootInfo);
                }
            } else {
                existingRootInfos.add(rootInfo);
            }

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
            return new TestTreeModel(path, buildPerRootInfos(), buildChildren());
        }

        private List<List<PerRootInfo>> buildPerRootInfos() {
            ImmutableList.Builder<List<PerRootInfo>> perRootInfosBuilder =
                ImmutableList.builderWithExpectedSize(perRootInfoBuilders.size());
            for (int i = 0; i < perRootInfoBuilders.size(); i++) {
                List<PerRootInfo.Builder> builders = perRootInfoBuilders.get(i);
                // Clean up per root info builders as we build, to let GC reclaim their memory.
                perRootInfoBuilders.set(i, ImmutableList.of());

                ImmutableList.Builder<PerRootInfo> infosBuilder = ImmutableList.builderWithExpectedSize(builders.size());
                for (PerRootInfo.Builder builder : builders) {
                    infosBuilder.add(builder.build());
                }
                perRootInfosBuilder.add(infosBuilder.build());
            }
            return perRootInfosBuilder.build();
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

    public Iterable<TestTreeModel> getChildrenOf(int rootIndex) {
        // There should only be one perRootInfo with children.
        PerRootInfo perRootInfoWithChildren = perRootInfo.get(rootIndex).stream()
            .filter(info -> !info.getChildren().isEmpty())
            .findFirst()
            .orElse(null);
        if (perRootInfoWithChildren == null) {
            return Collections.emptyList();
        }
        // Take a unique ordered set of the child names, to only return one result per unique child name.
        // Consumers of this should iterate over the getPerRootInfo() to get all results for a given child name.
        ImmutableSet<String> childNames = ImmutableSet.copyOf(perRootInfoWithChildren.getChildren());
        return Iterables.filter(children, c -> childNames.contains(c.path.segment));
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
