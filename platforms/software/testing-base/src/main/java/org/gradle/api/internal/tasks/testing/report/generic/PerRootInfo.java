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
import com.google.common.collect.Iterables;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputEntry;
import org.gradle.api.internal.tasks.testing.results.serializable.OutputRanges;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.tasks.testing.TestResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public abstract class PerRootInfo {
    public static final class Builder {
        private final long id;
        private final List<SerializableTestResult> results;
        private final List<OutputEntry> outputEntries;
        private final boolean isLeaf;
        private int totalLeafCount;
        private int failedLeafCount;
        private int skippedLeafCount;

        public Builder(
            long id,
            SerializableTestResult result,
            OutputRanges ranges,
            boolean isLeaf,
            int totalLeafCount,
            int failedLeafCount,
            int skippedLeafCount
        ) {
            this.id = id;
            this.results = new ArrayList<>(Collections.singletonList(result));
            this.outputEntries = new ArrayList<>(Collections.singletonList(new OutputEntry(id, ranges)));
            this.isLeaf = isLeaf;
            this.totalLeafCount = totalLeafCount;
            this.failedLeafCount = failedLeafCount;
            this.skippedLeafCount = skippedLeafCount;
        }

        public String getName() {
            return results.get(0).getName();
        }

        public boolean isLeaf() {
            return isLeaf;
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

        public void merge(Builder otherBuilder) {
            totalLeafCount += otherBuilder.totalLeafCount;
            failedLeafCount += otherBuilder.failedLeafCount;
            skippedLeafCount += otherBuilder.skippedLeafCount;

            if (!otherBuilder.getName().equals(getName())) {
                throw new IllegalArgumentException("Cannot merge PerRootInfo.Builder with different names: " + getName() + " and " + otherBuilder.getName());
            }
            results.addAll(otherBuilder.results);
            outputEntries.addAll(otherBuilder.outputEntries);
        }

        public PerRootInfo build() {
            if (isLeaf) {
                if (results.size() > 1) {
                    // We don't support this case because we don't need it currently, so it saves us some complexity.
                    throw new IllegalStateException("Cannot build a leaf PerRootInfo with multiple results");
                }

                // Sanity check:
                if (totalLeafCount != 1 || failedLeafCount > 1 || skippedLeafCount > 1) {
                    throw new IllegalStateException("Inconsistent leaf counts for leaf PerRootInfo: total=" + totalLeafCount + ", failed=" + failedLeafCount + ", skipped=" + skippedLeafCount);
                }

                return new SingleResultLeaf(
                    id,
                    results.get(0),
                    outputEntries.get(0).getOutputRanges()
                );
            }
            if (results.size() == 1) {
                return new SingleResultContainer(
                    id,
                    results.get(0),
                    outputEntries.get(0).getOutputRanges(),
                    totalLeafCount,
                    failedLeafCount,
                    skippedLeafCount
                );
            } else {
                return new MultiResultContainer(
                    id,
                    ImmutableList.copyOf(results),
                    ImmutableList.copyOf(outputEntries),
                    totalLeafCount,
                    failedLeafCount,
                    skippedLeafCount
                );
            }
        }
    }

    /**
     * Optimized representation for a single test result leaf, to avoid the overhead of lists.
     */
    private static final class SingleResultLeaf extends PerRootInfo {
        private final SerializableTestResult result;
        private final OutputRanges outputRanges;

        private SingleResultLeaf(long id, SerializableTestResult result, OutputRanges outputRanges) {
            super(id);
            this.result = result;
            this.outputRanges = outputRanges;
        }

        @Override
        public boolean isLeaf() {
            return true;
        }

        @Override
        public List<SerializableTestResult> getResults() {
            return Collections.singletonList(result);
        }

        @Override
        public List<OutputEntry> getOutputEntries() {
            return Collections.singletonList(new OutputEntry(getId(), outputRanges));
        }

        @Override
        public int getTotalLeafCount() {
            return 1;
        }

        @Override
        public int getFailedLeafCount() {
            return result.getResultType() == TestResult.ResultType.FAILURE ? 1 : 0;
        }

        @Override
        public int getSkippedLeafCount() {
            return result.getResultType() == TestResult.ResultType.SKIPPED ? 1 : 0;
        }

        @Override
        public Iterable<TestMetadataEvent> getMetadatas() {
            return result.getMetadatas();
        }
    }

    private static final class SingleResultContainer extends PerRootInfo {
        private final SerializableTestResult result;
        private final OutputRanges outputRanges;
        private final int totalLeafCount;
        private final int failedLeafCount;
        private final int skippedLeafCount;

        private SingleResultContainer(
            long id,
            SerializableTestResult result,
            OutputRanges outputRanges,
            int totalLeafCount,
            int failedLeafCount,
            int skippedLeafCount
        ) {
            super(id);
            this.result = result;
            this.outputRanges = outputRanges;
            this.totalLeafCount = totalLeafCount;
            this.failedLeafCount = failedLeafCount;
            this.skippedLeafCount = skippedLeafCount;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public List<SerializableTestResult> getResults() {
            return Collections.singletonList(result);
        }

        @Override
        public List<OutputEntry> getOutputEntries() {
            return Collections.singletonList(new OutputEntry(getId(), outputRanges));
        }

        @Override
        public int getTotalLeafCount() {
            return totalLeafCount;
        }

        @Override
        public int getFailedLeafCount() {
            return failedLeafCount;
        }

        @Override
        public int getSkippedLeafCount() {
            return skippedLeafCount;
        }

        @Override
        public Iterable<TestMetadataEvent> getMetadatas() {
            return result.getMetadatas();
        }
    }

    private static final class MultiResultContainer extends PerRootInfo {
        private final List<SerializableTestResult> results;
        private final List<OutputEntry> outputEntries;
        private final int totalLeafCount;
        private final int failedLeafCount;
        private final int skippedLeafCount;

        private MultiResultContainer(
            long id,
            List<SerializableTestResult> results,
            List<OutputEntry> outputEntries,
            int totalLeafCount,
            int failedLeafCount,
            int skippedLeafCount
        ) {
            super(id);
            this.results = results;
            this.outputEntries = outputEntries;
            this.totalLeafCount = totalLeafCount;
            this.failedLeafCount = failedLeafCount;
            this.skippedLeafCount = skippedLeafCount;
        }

        @Override
        public boolean isLeaf() {
            return false;
        }

        @Override
        public List<SerializableTestResult> getResults() {
            return results;
        }

        @Override
        public List<OutputEntry> getOutputEntries() {
            return outputEntries;
        }

        @Override
        public int getTotalLeafCount() {
            return totalLeafCount;
        }

        @Override
        public int getFailedLeafCount() {
            return failedLeafCount;
        }

        @Override
        public int getSkippedLeafCount() {
            return skippedLeafCount;
        }

        @Override
        public Iterable<TestMetadataEvent> getMetadatas() {
            return Iterables.concat(
                Iterables.transform(getResults(), SerializableTestResult::getMetadatas)
            );
        }
    }

    private final long id;

    private PerRootInfo(long id) {
        this.id = id;
    }

    /**
     * Returns true if this node is a leaf (has no children).
     *
     * @return true if this is a leaf node
     */
    public abstract boolean isLeaf();

    /**
     * Returns the test results associated with this node. Note that these are not typically separate individual test runs,
     * as leaves are not merged, but rather multiple results for a parent that was run multiple times, e.g.
     * if a class gets run on two test workers. It's up to the reporting layer to decide how to aggregate these.
     *
     * <p>
     * It's guaranteed that there is always at least one result returned by this method.
     * </p>
     *
     * @return the test results
     */
    public abstract List<SerializableTestResult> getResults();

    public long getId() {
        return id;
    }

    public abstract List<OutputEntry> getOutputEntries();

    public abstract int getTotalLeafCount();

    public abstract int getFailedLeafCount();

    public abstract int getSkippedLeafCount();

    public abstract Iterable<TestMetadataEvent> getMetadatas();
}
