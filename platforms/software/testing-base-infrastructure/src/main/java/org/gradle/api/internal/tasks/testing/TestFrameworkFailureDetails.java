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

import org.gradle.api.tasks.testing.TestFailureDetails;
import org.jspecify.annotations.NullMarked;

/**
 * A {@link TestFailureDetails} that indicates a failure in the test framework itself, like
 * a missing test framework, that shouldn't be reported a <em>failure of a test or tests</em>, but rather as a
 * <em>Gradle test task failure</em>.
 * <p>
 * This need to be treated differently, so that the errors are more visible instead of being buried in the
 * test report.
 */
@NullMarked
public final class TestFrameworkFailureDetails extends DefaultTestFailureDetails {
    public TestFrameworkFailureDetails(String message, String className, String stacktrace) {
        super(message, className, stacktrace);
    }

    @Override
    public boolean isStartupFailure() {
        return true;
    }
}
