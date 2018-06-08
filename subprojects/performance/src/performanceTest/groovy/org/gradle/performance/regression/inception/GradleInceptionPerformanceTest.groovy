/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.performance.regression.inception

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Issue
import spock.lang.Unroll

/**
 * Test Gradle performance against it's own build.
 *
 * Reasons for re-baselining:
 * - accept a regression in Gradle itself
 * - accept a regression in the Gradle build
 * - improvements to Gradle or its build!
 *
 * Reasons for breaking:
 *   - e.g. change in Gradle that breaks the Gradle build
 */
@Issue('https://github.com/gradle/gradle-private/issues/1313')
class GradleInceptionPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll
    def "#tasks on the gradle build comparing gradle"() {
        given:
        runner.testProject = "gradleBuildCurrent"
        runner.tasksToRun = tasks.split(' ')
        runner.targetVersions = ["4.9-20180607113442+0000"]

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        tasks  | _
        'help' | _
    }
}
