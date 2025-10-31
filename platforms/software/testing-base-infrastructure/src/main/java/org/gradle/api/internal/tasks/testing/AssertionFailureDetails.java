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

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public class AssertionFailureDetails extends DefaultTestFailureDetails {
    @Nullable
    private final String expected;
    @Nullable
    private final String actual;

    public AssertionFailureDetails(@Nullable String message, String className, String stacktrace, @Nullable String expected, @Nullable String actual) {
        super(message, className, stacktrace);
        this.expected = expected;
        this.actual = actual;
    }

    @Override
    public boolean isAssertionFailure() {
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

    @Override
    public String toString() {
        return "assertion " + super.toString();
    }
}
