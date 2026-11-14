/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.results;

import org.gradle.api.internal.tasks.testing.GeneratedTestReport;
import org.gradle.internal.service.scopes.EventScope;
import org.gradle.internal.service.scopes.Scope;
import org.gradle.internal.service.scopes.StatefulListener;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Reports final test execution results intended for aggregate reporting.
 */
@NullMarked
@StatefulListener
@EventScope(Scope.Build.class)
public interface TestExecutionResultsListener {

    /**
     * The results of a single test execution.
     *
     * @param displayName the display name of the root of the test execution
     * @param failed whether the test execution as a whole failed, which can be true even if no individual test failed
     * @param report the report generated for the test execution, if any
     */
    record Execution(
        String displayName,
        long startTime,
        long endTime,
        long testCount,
        long failedCount,
        long skippedCount,
        boolean failed,
        @Nullable GeneratedTestReport report
    ) {}

    /**
     * Called when a test execution is complete and its reports have been generated.
     */
    void executionResultsAvailable(Execution execution);

}
