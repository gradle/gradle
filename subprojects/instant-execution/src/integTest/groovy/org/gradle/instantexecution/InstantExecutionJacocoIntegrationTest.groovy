/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution

import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest
import org.gradle.util.Requires

import static org.gradle.util.TestPrecondition.JDK14_OR_EARLIER

@Requires(JDK14_OR_EARLIER)
class InstantExecutionJacocoIntegrationTest extends AbstractInstantExecutionIntegrationTest {

    def "can use jacoco"() {

        given:
        new JavaProjectUnderTest(testDirectory).writeBuildScript().writeSourceFiles()
        def htmlReportDir = file("build/reports/jacoco/test/html")

        and:
        def instantExecution = newInstantExecutionFixture()
        def expectedStoreProblemCount = 4
        def expectedStoreProblems = [
            "field 'val\$testTaskProvider' from type 'org.gradle.testing.jacoco.plugins.JacocoPlugin\$11': cannot serialize object of type 'org.gradle.api.tasks.testing.Test', a subtype of 'org.gradle.api.Task', as these are not supported with the configuration cache.",
            "field 'project' from type 'org.gradle.testing.jacoco.plugins.JacocoPluginExtension': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.",
            "field 'project' from type 'org.gradle.testing.jacoco.plugins.JacocoPlugin': cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache."
        ]
        def expectedLoadProblemCount = 5
        def expectedLoadProblems = [
            "field 'val\$testTaskProvider' from type 'org.gradle.testing.jacoco.plugins.JacocoPlugin\$11': cannot deserialize object of type 'org.gradle.api.Task' as these are not supported with the configuration cache.",
            "field 'val\$testTaskProvider' from type 'org.gradle.testing.jacoco.plugins.JacocoPlugin\$11': value 'undefined' is not assignable to 'org.gradle.api.tasks.TaskProvider'",
            "field 'project' from type 'org.gradle.testing.jacoco.plugins.JacocoPluginExtension': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.",
            "field 'project' from type 'org.gradle.testing.jacoco.plugins.JacocoPlugin': cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache."
        ]

        when:
        instantFails 'test', 'jacocoTestReport'

        then:
        problems.assertFailureHasProblems(failure) {
            withUniqueProblems(expectedStoreProblems)
            withTotalProblemsCount(expectedStoreProblemCount)
            withProblemsWithStackTraceCount(0)
        }

        when:
        instantRunLenient 'test', 'jacocoTestReport'

        then:
        instantExecution.assertStateLoaded()
        problems.assertResultHasProblems(result) {
            withTotalProblemsCount(expectedLoadProblemCount)
            withUniqueProblems(expectedLoadProblems)
            withProblemsWithStackTraceCount(0)
        }
        htmlReportDir.assertIsDir()

        when:
        succeeds 'clean'
        htmlReportDir.assertDoesNotExist()

        and:
        instantFails 'test', 'jacocoTestReport'

        then:
        instantExecution.assertStateLoaded()
        problems.assertFailureHasProblems(failure) {
            withTotalProblemsCount(expectedLoadProblemCount)
            withUniqueProblems(expectedLoadProblems)
            withProblemsWithStackTraceCount(0)
        }

        when:
        instantRunLenient 'test', 'jacocoTestReport'

        then:
        instantExecution.assertStateLoaded()
        htmlReportDir.assertIsDir()
    }
}
