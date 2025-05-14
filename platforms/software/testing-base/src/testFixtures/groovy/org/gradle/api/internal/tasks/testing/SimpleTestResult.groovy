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

package org.gradle.api.internal.tasks.testing

import org.gradle.api.tasks.testing.TestFailure
import org.gradle.api.tasks.testing.TestResult

class SimpleTestResult implements TestResult {
    TestResult.ResultType resultType = TestResult.ResultType.SUCCESS
    List<Throwable> exceptions = []
    Throwable exception = exceptions[0]
    TestFailure assumptionFailure = null
    List<TestFailure> failures
    long startTime = 0
    long endTime = startTime + 100
    long testCount = 1
    long successfulTestCount = 1
    long failedTestCount = 0
    long skippedTestCount = 0

    SimpleTestResult(long endTime = 100) {
        this.endTime = endTime
    }
}
