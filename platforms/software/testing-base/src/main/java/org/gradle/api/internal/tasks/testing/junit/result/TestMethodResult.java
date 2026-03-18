/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.junit.result;

import org.gradle.api.tasks.testing.TestMetadataEvent;
import org.gradle.api.internal.tasks.testing.results.serializable.SerializableFailure;
import org.gradle.api.tasks.testing.TestResult;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class TestMethodResult {
    private final long id;
    private final String name;
    private final String displayName;
    private final TestResult.ResultType resultType;
    private final long duration;
    private final long endTime;
    private final List<TestMetadataEvent> metadatas;
    private final List<SerializableFailure> failures;

    private SerializableFailure assumptionFailure = null;

    public TestMethodResult(long id, String name, String displayName, TestResult.ResultType resultType, long duration, long endTime, List<TestMetadataEvent> metadatas) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be > 0");
        }
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.resultType = resultType;
        this.duration = duration;
        this.endTime = endTime;
        this.metadatas = metadatas;
        this.failures = new ArrayList<>();
    }

    // TODO: These could be folded into the constructor as well
    public TestMethodResult addFailure(String message, String stackTrace, String exceptionType) {
        this.failures.add(new SerializableFailure(message, stackTrace, exceptionType));
        return this;
    }

    public TestMethodResult setAssumptionFailure(@Nullable String message, String stackTrace, String exceptionType) {
        this.assumptionFailure = new SerializableFailure(message == null ? "(no message)" : message, stackTrace, exceptionType);
        return this;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDisplayName() {
        return displayName;
    }

    public List<SerializableFailure> getFailures() {
        return failures;
    }

    @Nullable
    public SerializableFailure getAssumptionFailure() {
        return assumptionFailure;
    }

    public TestResult.ResultType getResultType() {
        return resultType;
    }

    public long getDuration() {
        return duration;
    }

    public long getEndTime() {
        return endTime;
    }

    public List<TestMetadataEvent> getMetadatas() {
        return metadatas;
    }
}
