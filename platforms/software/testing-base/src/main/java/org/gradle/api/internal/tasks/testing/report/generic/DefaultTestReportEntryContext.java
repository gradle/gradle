/*
 * Copyright 2026 the original author or authors.
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
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableTestResult;
import org.gradle.api.tasks.testing.TestDescriptor;
import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.tasks.testing.TestReportEntryContext;
import org.gradle.api.tasks.testing.TestResult;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;

/**
 * Exposes a {@link PerRootInfo} to custom test report entry contributors.
 */
final class DefaultTestReportEntryContext implements TestReportEntryContext {
    private final TestDescriptor descriptor;
    private final TestResult.ResultType resultType;
    private final Collection<TestMetadataEvent> metadataEvents;

    static DefaultTestReportEntryContext create(PerRootInfo info, TestResult.ResultType resultType) {
        return new DefaultTestReportEntryContext(
            new ReportTestDescriptor(info),
            resultType,
            ImmutableList.copyOf(info.getMetadatas())
        );
    }

    private DefaultTestReportEntryContext(
        TestDescriptor descriptor,
        TestResult.ResultType resultType,
        Collection<TestMetadataEvent> metadataEvents
    ) {
        this.descriptor = descriptor;
        this.resultType = resultType;
        this.metadataEvents = metadataEvents;
    }

    @Override
    public TestDescriptor getTestDescriptor() {
        return descriptor;
    }

    @Override
    public TestResult.ResultType getResultType() {
        return resultType;
    }

    @Override
    public Collection<TestMetadataEvent> getMetadataEvents() {
        return metadataEvents;
    }

    /**
     * A descriptor for stored test results. The parent is not available.
     */
    private static final class ReportTestDescriptor implements TestDescriptor {
        private final String name;
        private final String displayName;
        @Nullable
        private final String className;
        private final boolean composite;

        private ReportTestDescriptor(PerRootInfo info) {
            List<SerializableTestResult> results = info.getResults();
            SerializableTestResult first = results.get(0);
            this.name = first.getName();
            this.displayName = SerializableTestResult.getCombinedDisplayName(results);
            this.className = first.getClassName();
            this.composite = !info.isLeaf();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDisplayName() {
            return displayName;
        }

        @Override
        @Nullable
        public String getClassName() {
            return className;
        }

        @Override
        public boolean isComposite() {
            return composite;
        }

        @Override
        @Nullable
        public TestDescriptor getParent() {
            return null;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }
}
