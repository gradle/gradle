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

package org.gradle.api.internal.tasks.testing.results.serializable;

import java.util.OptionalLong;

/**
 * A test result with information for looking up parents or output.
 */
public final class OutputTrackedResult {
    private final OutputEntry outputEntry;
    private final SerializableTestResult testResult;
    private final long parentId;

    OutputTrackedResult(OutputEntry outputEntry, SerializableTestResult testResult, long parentId) {
        this.outputEntry = outputEntry;
        this.testResult = testResult;
        this.parentId = parentId;
    }

    public SerializableTestResult getInnerResult() {
        return testResult;
    }

    public long getId() {
        return outputEntry.id;
    }

    public OutputEntry getOutputEntry() {
        return outputEntry;
    }

    public OptionalLong getParentId() {
        return parentId == 0 ? OptionalLong.empty() : OptionalLong.of(parentId);
    }

    public OutputTrackedResult withInnerResult(SerializableTestResult newInnerResult) {
        return new OutputTrackedResult(outputEntry, newInnerResult, parentId);
    }
}
