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

/**
 * Represents an entry in the test output events file.
 *
 * <p>
 * Combination of {@link OutputRanges} and an ID.
 * </p>
 */
public final class OutputEntry {
    private final long id;
    private final OutputRanges outputRanges;

    public OutputEntry(long id, OutputRanges outputRanges) {
        this.id = id;
        this.outputRanges = outputRanges;
    }

    public long getId() {
        return id;
    }

    public OutputRanges getOutputRanges() {
        return outputRanges;
    }
}
