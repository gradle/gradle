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
import org.gradle.performance.mutator.ApplyNonAbiChangeToJavaSourceFileMutator
import spock.lang.Unroll

class JavaTestChangePerformanceTest extends AbstractCrossVersionPerformanceTest {

    @Unroll
    def "test change in #testProject"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['test']
        runner.targetVersions = ["3.5-20170221000043+0000"]
        runner.warmUpRuns = warmUpRuns
        runner.runs = runs
        runner.addBuildExperimentListener(new ApplyNonAbiChangeToJavaSourceFileMutator(fileToChange))

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                 | warmUpRuns | runs | fileToChange
        "largeMonolithicJavaProject"| 2          | 6    | "src/main/java/org/gradle/test/performancenull_200/Productionnull_20000.java"
        "largeJavaMultiProject"     | 2          | 6    | "project200/src/main/java/org/gradle/test/performance200_1/Production200_1.java"
    }
}
