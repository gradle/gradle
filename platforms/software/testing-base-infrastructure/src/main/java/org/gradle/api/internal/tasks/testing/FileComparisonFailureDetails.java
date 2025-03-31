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

import javax.annotation.Nullable;

@org.gradle.api.NonNullApi
public class FileComparisonFailureDetails extends DefaultTestFailureDetails {
    private final String expected;
    private final String actual;
    private final byte[] expectedContent;
    private final byte[] actualContent;

    public FileComparisonFailureDetails(String message, String className, String stacktrace, String expected, String actual, byte[] expectedContent, byte[] actualContent) {
        super(message, className, stacktrace);
        this.expected = expected;
        this.actual = actual;
        this.expectedContent = expectedContent;
        this.actualContent = actualContent;
    }

    @Override
    public boolean isAssertionFailure() {
        return true;
    }

    @Override
    public boolean isFileComparisonFailure() {
        return true;
    }

    @Nullable
    @Override
    public String getExpected() {
        return expected;
    }

    @Nullable
    @Override
    public String getActual() {
        return actual;
    }

    @Nullable
    @Override
    public byte[] getExpectedContent() {
        return expectedContent;
    }

    @Nullable
    @Override
    public byte[] getActualContent() {
        return actualContent;
    }

    @Override
    public String toString() {
        return "file comparison " + super.toString();
    }
}
