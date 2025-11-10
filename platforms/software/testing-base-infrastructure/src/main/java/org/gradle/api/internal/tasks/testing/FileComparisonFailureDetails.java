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

package org.gradle.api.internal.tasks.testing;

import org.jspecify.annotations.Nullable;

@org.jspecify.annotations.NullMarked
public class FileComparisonFailureDetails extends AssertionFailureDetails {
    private final byte @Nullable [] expectedContent;
    private final byte @Nullable [] actualContent;

    public FileComparisonFailureDetails(@Nullable String message, String className, String stacktrace, @Nullable String expected, @Nullable String actual, byte @Nullable [] expectedContent, byte @Nullable [] actualContent) {
        super(message, className, stacktrace, expected, actual);
        this.expectedContent = expectedContent;
        this.actualContent = actualContent;
    }

    @Override
    public boolean isFileComparisonFailure() {
        return true;
    }

    @Override
    public byte @Nullable [] getExpectedContent() {
        return expectedContent;
    }

    @Override
    public byte @Nullable [] getActualContent() {
        return actualContent;
    }

    @Override
    public String toString() {
        return "file comparison " + super.toString();
    }
}
