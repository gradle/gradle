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
import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.testing.TestMetadataEvent;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputEntry;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputTrackedResult;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public final class PerRootInfo {
    public static final class Builder {
        private OutputTrackedResult outputTrackedResult;
        private final List<String> children;
        private final BitSet childIsLeaf;
        private int totalLeafCount;
        private int failedLeafCount;
        private int skippedLeafCount;

        public Builder(OutputTrackedResult result, List<String> childNames, BitSet childIsLeaf, int totalLeafCount, int failedLeafCount, int skippedLeafCount) {
            this.outputTrackedResult = result;
            this.children = new ArrayList<>(childNames);
            this.childIsLeaf = childIsLeaf;
            this.totalLeafCount = totalLeafCount;
            this.failedLeafCount = failedLeafCount;
            this.skippedLeafCount = skippedLeafCount;
        }

        public String getName() {
            return outputTrackedResult.getInnerResult().getName();
        }

        public boolean isLeaf() {
            return children.isEmpty();
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

        public void merge(PerRootInfo.Builder otherBuilder) {
            Set<String> knownChildren = ImmutableSet.copyOf(this.children);
            List<String> strings = otherBuilder.children;
            for (int i = 0; i < strings.size(); i++) {
                String newChild = strings.get(i);
                boolean newChildIsLeaf = otherBuilder.childIsLeaf.get(i);
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

            totalLeafCount += otherBuilder.totalLeafCount;
            failedLeafCount += otherBuilder.failedLeafCount;
            skippedLeafCount += otherBuilder.skippedLeafCount;

            SerializableTestResult otherResult = otherBuilder.outputTrackedResult.getInnerResult();
            SerializableTestResult mergedResult = outputTrackedResult.getInnerResult().merge(otherResult);
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

        public PerRootInfo build() {
            return new PerRootInfo(
                outputTrackedResult,
                ImmutableList.copyOf(children),
                totalLeafCount,
                failedLeafCount,
                skippedLeafCount
            );
        }
    }

    private final SerializableTestResult result;
    private final long id;
    @Nullable
    private final OutputEntry outputEntry;
    private final ImmutableList<String> children;
    private final int totalLeafCount;
    private final int failedLeafCount;
    private final int skippedLeafCount;

    private PerRootInfo(OutputTrackedResult outputTrackedResult, ImmutableList<String> children, int totalLeafCount, int failedLeafCount, int skippedLeafCount) {
        this.result = outputTrackedResult.getInnerResult();
        this.id = outputTrackedResult.getId();
        this.outputEntry = outputTrackedResult.getOutputEntry().hasOutput()
            ? outputTrackedResult.getOutputEntry()
            : null;
        this.children = children;
        this.totalLeafCount = totalLeafCount;
        this.failedLeafCount = failedLeafCount;
        this.skippedLeafCount = skippedLeafCount;
    }

    public SerializableTestResult getResult() {
        return result;
    }

    public long getId() {
        return id;
    }

    @Nullable
    public OutputEntry getOutputEntry() {
        return outputEntry;
    }

    public ImmutableList<String> getChildren() {
        return children;
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

    public List<TestMetadataEvent> getMetadatas() {
        return result.getMetadatas();
    }
}
