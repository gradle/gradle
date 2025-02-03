/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.report

import groovy.transform.SelfType
import org.gradle.api.internal.tasks.testing.GenericTestReportGenerator
import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.logging.ConsoleRenderer

/**
 * A trait to be applied to tests that verify the results of the {@link GenericTestReportGenerator}.
 */
@SelfType(AbstractIntegrationSpec)
trait VerifiesGenericTestReportResults {
    String resultsUrlFor(String name) {
        def expectedReportFile = file("build/reports/tests/${name}/index.html")
        String renderedUrl = new ConsoleRenderer().asClickableFileUrl(expectedReportFile);
        renderedUrl
    }

    GenericTestExecutionResult resultsFor(String name) {
        return new GenericHtmlTestExecutionResult(testDirectory, "build/reports/tests/${name}")
    }

    GenericTestExecutionResult aggregateResults() {
        return new GenericHtmlTestExecutionResult(testDirectory, "build/reports/aggregate-test-results")
    }
}
