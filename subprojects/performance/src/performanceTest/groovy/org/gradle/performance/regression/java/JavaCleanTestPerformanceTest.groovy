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

package org.gradle.performance.regression.java

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import spock.lang.Unroll

class JavaCleanTestPerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll
    def "clean test on #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['cleanTest', 'test']
        runner.gradleOpts = ["-Xms${memory}", "-Xmx${memory}"]
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                        | memory | warmUpRuns | runs
        "largeMonolithicJavaProject"       | '2g'     | 2          | 6
        "largeJavaMultiProject"            | '2g'     | 2          | 6
        "largeMonolithicJavaProjectTestNG" | '2g'     | 2          | 6
        "largeJavaMultiProjectTestNG"      | '2g'     | 2          | 6
    }
}
