/*
 * Copyright 2022 the original author or authors.
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

import org.gradle.api.tasks.testing.TestFailureDetails;

import javax.annotation.Nullable;

public class DefaultTestFailureDetails implements TestFailureDetails {

    private final String message;
    private final String className;
    private final String stacktrace;

    public DefaultTestFailureDetails(String message, String className, String stacktrace) {
        this.message = message;
        this.className = className;
        this.stacktrace = stacktrace;
    }

    @Override
    public String getMessage() {
        return message;
    }

    @Override
    public String getClassName() {
        return className;
    }

    @Override
    public String getStacktrace() {
        return stacktrace;
    }

    @Override
    public boolean isAssertionFailure() {
        return false;
    }

    @Override
    public boolean isFileComparisonFailure() {
        return false;
    }

    @Override
    public boolean isAssumptionFailure() {
        return false;
    }

    @Override
    public String getExpected() {
        return null;
    }

    @Override
    public String getActual() {
        return null;
    }

    @Nullable
    @Override
    public byte[] getExpectedContent() {
        return null;
    }

    @Nullable
    @Override
    public byte[] getActualContent() {
        return null;
    }

    @Override
    public String toString() {
        return "{" +
            "className='" + className + '\'' +
            ", message='" + message + '\'' +
            '}';
    }
}
