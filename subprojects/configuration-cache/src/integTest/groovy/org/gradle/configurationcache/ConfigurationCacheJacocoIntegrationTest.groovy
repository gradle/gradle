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

package org.gradle.configurationcache

import org.gradle.testing.jacoco.plugins.fixtures.JavaProjectUnderTest

class ConfigurationCacheJacocoIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "can use jacoco"() {

        given:
        new JavaProjectUnderTest(testDirectory).writeBuildScript().writeSourceFiles()
        def htmlReportDir = file("build/reports/jacoco/test/html")

        and:
        def configurationCache = newConfigurationCacheFixture()
        def expectedStoreProblemCount = 4
        def expectedStoreProblems = [
            "Task `:jacocoTestReport` of type `org.gradle.testing.jacoco.tasks.JacocoReport`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache.",
            "Task `:jacocoTestReport` of type `org.gradle.testing.jacoco.tasks.JacocoReport`: cannot serialize object of type 'org.gradle.api.tasks.testing.Test', a subtype of 'org.gradle.api.Task', as these are not supported with the configuration cache.",
            "Task `:test` of type `org.gradle.api.tasks.testing.Test`: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache."
        ]
        def expectedLoadProblemCount = 5
        def expectedLoadProblems = [
            "Task `:jacocoTestReport` of type `org.gradle.testing.jacoco.tasks.JacocoReport`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache.",
            "Task `:jacocoTestReport` of type `org.gradle.testing.jacoco.tasks.JacocoReport`: cannot deserialize object of type 'org.gradle.api.Task' as these are not supported with the configuration cache.",
            "Task `:jacocoTestReport` of type `org.gradle.testing.jacoco.tasks.JacocoReport`: value 'undefined' is not assignable to 'org.gradle.api.tasks.TaskProvider'",
            "Task `:test` of type `org.gradle.api.tasks.testing.Test`: cannot deserialize object of type 'org.gradle.api.Project' as these are not supported with the configuration cache."
        ]

        when:
        configurationCacheRunLenient 'test', 'jacocoTestReport'

        then:
        problems.assertResultHasProblems(result) {
            withUniqueProblems(expectedStoreProblems)
            withTotalProblemsCount(expectedStoreProblemCount)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient 'test', 'jacocoTestReport'

        then:
        configurationCache.assertStateLoaded()
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
        configurationCacheFails 'test', 'jacocoTestReport'

        then:
        configurationCache.assertStateLoaded()
        problems.assertFailureHasProblems(failure) {
            withTotalProblemsCount(expectedLoadProblemCount)
            withUniqueProblems(expectedLoadProblems)
            withProblemsWithStackTraceCount(0)
        }

        when:
        configurationCacheRunLenient 'test', 'jacocoTestReport'

        then:
        configurationCache.assertStateLoaded()
        htmlReportDir.assertIsDir()
    }
}
