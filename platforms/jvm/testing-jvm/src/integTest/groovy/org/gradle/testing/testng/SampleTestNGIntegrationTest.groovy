/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.testing.testng


import org.gradle.api.internal.tasks.testing.report.generic.GenericHtmlTestExecutionResult
import org.gradle.api.internal.tasks.testing.report.generic.GenericTestExecutionResult
import org.gradle.api.tasks.testing.TestResult
import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.TestNGExecutionResult
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SampleTestNGIntegrationTest extends AbstractIntegrationTest {

    @Rule
    public final Sample sample = new Sample(testDirectoryProvider)

    @Before
    void setUp() {
        executer.withRepositoryMirrors()
    }

    @Test
    @UsesSample('testing/testng-suitexmlbuilder')
    void suiteXmlBuilder() {
        def testDir = sample.dir.file('groovy')
        executer.inDirectory(testDir).withTasks('clean', 'test').run()

        def result = resultsFor(testDir)
        result.testPath(':org.gradle.testng.UserImplTest').onlyRoot().assertChildCount(1, 0).assertOnlyChildrenExecuted("testOkFirstName")
    }

    @Test
    @UsesSample('testing/testng-java-passing')
    void javaPassing() {
        def testDir = sample.dir.file('groovy')
        executer.inDirectory(testDir).withTasks('clean', 'test').run()

        def result = resultsFor(testDir)
        result.assertAtLeastTestPathsExecuted('org.gradle.OkTest', 'org.gradle.ConcreteTest')
        result.testPath('org.gradle.OkTest', 'passingTest').onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        result.testPath('org.gradle.OkTest', 'expectedFailTest').onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        result.testPath('org.gradle.ConcreteTest', 'ok').onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)
        result.testPath('org.gradle.ConcreteTest', 'alsoOk').onlyRoot().assertHasResult(TestResult.ResultType.SUCCESS)

        def testNgResult = new TestNGExecutionResult(testDir)
        testNgResult.testClass('org.gradle.SuiteSetup').assertConfigMethodPassed('setupSuite')
        testNgResult.testClass('org.gradle.SuiteCleanup').assertConfigMethodPassed('cleanupSuite')
        testNgResult.testClass('org.gradle.TestSetup').assertConfigMethodPassed('setupTest')
        testNgResult.testClass('org.gradle.TestCleanup').assertConfigMethodPassed('cleanupTest')
    }

    private GenericHtmlTestExecutionResult resultsFor(File rootDir) {
        return new GenericHtmlTestExecutionResult(rootDir, "build/reports/tests/test", GenericTestExecutionResult.TestFramework.TEST_NG)
    }
}
