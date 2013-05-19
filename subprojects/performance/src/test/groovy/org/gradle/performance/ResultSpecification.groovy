/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.performance

import org.gradle.performance.fixture.PerformanceResults
import org.gradle.performance.measure.Amount
import org.gradle.performance.measure.DataAmount
import org.gradle.performance.measure.Duration
import org.gradle.performance.measure.MeasuredOperation
import spock.lang.Specification

class ResultSpecification extends Specification {
    PerformanceResults results(Map<String, ?> options = [:]) {
        def results = new PerformanceResults()
        results.testId = "test-id"
        results.displayName = "some test"
        results.testProject = "test-project"
        results.tasks = ["clean", "build"]
        results.args = []
        results.operatingSystem = "some os"
        results.jvm = "java 6"
        results.versionUnderTest = "1.7-rc-1"
        options.each { key, value -> results."$key" = value }
        return results
    }

    MeasuredOperation operation(Map<String, Object> args = [:]) {
        def operation = new MeasuredOperation()
        operation.executionTime = args.executionTime instanceof Amount ? args.executionTime : Duration.millis(args?.executionTime ?: 120)
        operation.totalMemoryUsed = args.heapUsed instanceof Amount ? args.heapUsed : DataAmount.bytes(args?.heapUsed ?: 1024)
        operation.exception = args?.failure
        return operation
    }
}
