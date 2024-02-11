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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.integtests.fixtures.DefaultTestExecutionResult
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

        def result = new DefaultTestExecutionResult(testDir)
        result.assertTestClassesExecuted('org.gradle.testng.UserImplTest')
        result.testClass('org.gradle.testng.UserImplTest').assertTestsExecuted('testOkFirstName')
        result.testClass('org.gradle.testng.UserImplTest').assertTestPassed('testOkFirstName')
    }

    @Test
    @UsesSample('testing/testng-java-passing')
    void javaPassing() {
        def testDir = sample.dir.file('groovy')
        executer.inDirectory(testDir).withTasks('clean', 'test').run()

        def result = new TestNGExecutionResult(testDir)
        result.assertTestClassesExecuted('org.gradle.OkTest', 'org.gradle.ConcreteTest', 'org.gradle.SuiteSetup', 'org.gradle.SuiteCleanup', 'org.gradle.TestSetup', 'org.gradle.TestCleanup')
        result.testClass('org.gradle.OkTest').assertTestsExecuted('passingTest', 'expectedFailTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('passingTest')
        result.testClass('org.gradle.OkTest').assertTestPassed('expectedFailTest')
        result.testClass('org.gradle.ConcreteTest').assertTestsExecuted('ok', 'alsoOk')
        result.testClass('org.gradle.ConcreteTest').assertTestPassed('ok')
        result.testClass('org.gradle.ConcreteTest').assertTestPassed('alsoOk')
        result.testClass('org.gradle.SuiteSetup').assertConfigMethodPassed('setupSuite')
        result.testClass('org.gradle.SuiteCleanup').assertConfigMethodPassed('cleanupSuite')
        result.testClass('org.gradle.TestSetup').assertConfigMethodPassed('setupTest')
        result.testClass('org.gradle.TestCleanup').assertConfigMethodPassed('cleanupTest')
    }
}
