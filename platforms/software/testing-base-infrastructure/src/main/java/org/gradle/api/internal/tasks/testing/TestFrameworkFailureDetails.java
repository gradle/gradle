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

package org.gradle.api.internal.tasks.testing;

import org.gradle.api.tasks.testing.TestFailureDetails;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * {@link TestFailureDetails} produced when the test framework itself signals a failure outside
 * the body of a test method (e.g. a TestNG class whose constructor throws, a JUnit Jupiter
 * {@code @BeforeAll} hook that aborts the container, or a JUnit 4 custom runner whose
 * {@code run()} method throws). Distinct subtype so the failure can be identified across the
 * worker/serializer boundary and surfaced to users even under test logging configurations that
 * would otherwise filter out the descriptor they are attached to.
 *
 * @see TestFailureDetails#isFrameworkFailure()
 */
@NullMarked
public class TestFrameworkFailureDetails extends DefaultTestFailureDetails {
    public TestFrameworkFailureDetails(@Nullable String message, String className, String stacktrace) {
        super(message, className, stacktrace);
    }

    @Override
    public boolean isFrameworkFailure() {
        return true;
    }

    /**
     * Prefixed with {@code "framework-failure "} to disambiguate this failure type in log output,
     * since the inherited representation only includes the class name and message.
     */
    @Override
    public String toString() {
        return "framework-failure " + super.toString();
    }
}
