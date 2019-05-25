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

package org.gradle.testing

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.tasks.testing.junit.result.TestClassResult
import org.gradle.api.internal.tasks.testing.junit.result.TestResultSerializer
import org.gradle.api.tasks.testing.TestResult
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.openmbee.junit.model.JUnitTestSuite
import spock.lang.Specification

import static org.gradle.testing.DistributedPerformanceTest.ScenarioResult

class RerunableDistributedPerformanceTestTest extends Specification {
    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    ProjectInternal project

    def setup() {
        project = ProjectBuilder
            .builder()
            .withProjectDir(temporaryFolder.root)
            .build()
    }

    def 'can write distributed test results to binResultDir'() {
        given:
        Map<String, ScenarioResult> finishedResults = [
            scenario1: createScenario('org.gradle.test.MyTest', 'scenario1', 'SUCCESS'),
            scenario2: createScenario('org.gradle.test.MyTest', 'scenario2', 'FAILURE'),
            scenario3: createScenario('org.gradle.test.MyTest2', 'scenario3', 'SUCCESS')
        ]
        when:
        def task = project.tasks.create('distributedPerformanceTest', RerunableDistributedPerformanceTest)
        task.finishedBuilds = finishedResults
        task.binResultsDir = project.buildDir
        task.binResultsDir.mkdirs()
        task.writeBinaryResults()

        then:
        def classResults = readClassResults(task.binResultsDir)
        classResults.size() == 2
        def class1 = classResults.find { it.className == 'org.gradle.test.MyTest' }
        def class2 = classResults.find { it.className == 'org.gradle.test.MyTest2' }

        class1.results.size() == 2
        class2.results.size() == 1

        def method1 = class1.results.find { it.name == 'scenario1' }
        def method2 = class1.results.find { it.name == 'scenario2' }
        def method3 = class2.results.find { it.name == 'scenario3' }

        method1.resultType == TestResult.ResultType.SUCCESS
        method2.resultType == TestResult.ResultType.FAILURE
        method3.resultType == TestResult.ResultType.SUCCESS
    }

    private List<TestClassResult> readClassResults(File binResultsDir) {
        List<TestClassResult> results = []
        new TestResultSerializer(binResultsDir).read {
            results.add(it)
        }
        return results
    }

    ScenarioResult createScenario(String className, String methodName, String status) {
        JUnitTestSuite testSuite = new JUnitTestSuite()
        testSuite.name = className
        return new ScenarioResult(
            name: methodName,
            buildResult: [status: status],
            testSuite: testSuite
        )
    }
}
